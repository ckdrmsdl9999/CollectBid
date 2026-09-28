param(
    [string]$BaseUrl = 'http://localhost:8080',
    [ValidateRange(10, 45)][int]$AuctionSeconds = 15
)

$ErrorActionPreference = 'Stop'
$BaseUrl = $BaseUrl.TrimEnd('/')

function Invoke-CollectBid {
    param([string]$Method, [string]$Path, [object]$Body = $null, [string]$Token = '', [string]$RequestKey = '')
    $requestHeaders = @{}
    if ($Token) { $requestHeaders.Authorization = "Bearer $Token" }
    if ($RequestKey) { $requestHeaders['Idempotency-Key'] = $RequestKey }
    $requestOptions = @{ Method = $Method; Uri = "$BaseUrl$Path"; Headers = $requestHeaders; TimeoutSec = 20 }
    if ($null -ne $Body) {
        $requestOptions.ContentType = 'application/json; charset=utf-8'
        $requestOptions.Body = [System.Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Depth 8))
    }
    Invoke-RestMethod @requestOptions
}

$health = Invoke-CollectBid GET '/actuator/health'
if ($health.status -ne 'UP') { throw 'The application is not healthy.' }

$suffix = [guid]::NewGuid().ToString('N').Substring(0, 10)
$demoPassword = 'Demo-' + [guid]::NewGuid().ToString('N') + '!'
$sellerEmail = "seller-$suffix@example.com"
$buyerEmail = "buyer-$suffix@example.com"
Write-Host 'Creating two demo accounts and two products. Existing data is preserved.'
$seller = Invoke-CollectBid POST '/api/members' @{ email = $sellerEmail; password = $demoPassword; nickname = 'Demo seller' }
$buyer = Invoke-CollectBid POST '/api/members' @{ email = $buyerEmail; password = $demoPassword; nickname = 'Demo buyer' }
$sellerLogin = Invoke-CollectBid POST '/api/auth/login' @{ email = $sellerEmail; password = $demoPassword }
$buyerLogin = Invoke-CollectBid POST '/api/auth/login' @{ email = $buyerEmail; password = $demoPassword }
$sellerToken = $sellerLogin.accessToken
$buyerToken = $buyerLogin.accessToken

$product = Invoke-CollectBid POST '/api/products' @{
    title = "Pikachu demo $suffix"; description = 'Sealed collectible card'; category = 'TCG'; condition = 'SEALED'
} $sellerToken
$auctionEnd = [DateTimeOffset]::UtcNow.AddSeconds($AuctionSeconds)
$auction = Invoke-CollectBid POST '/api/auctions/forward' @{
    productId = $product.id; startingPrice = 10000; minIncrement = 1000; endsAt = $auctionEnd.ToString('o')
} $sellerToken
$requestKey = [guid]::NewGuid().ToString()
$bid = Invoke-CollectBid POST "/api/auctions/$($auction.id)/bids" @{ amount = 10000 } $buyerToken $requestKey
$replay = Invoke-CollectBid POST "/api/auctions/$($auction.id)/bids" @{ amount = 10000 } $buyerToken $requestKey
if ($bid.id -ne $replay.id) { throw 'Bid replay created a duplicate.' }
Write-Host "Forward auction $($auction.id): bid accepted; retry returned the same bid."

$secondProduct = Invoke-CollectBid POST '/api/products' @{
    title = "Charizard demo $suffix"; description = 'Another sealed collectible'; category = 'TCG'; condition = 'SEALED'
} $sellerToken
$reverse = Invoke-CollectBid POST '/api/auctions/reverse' @{
    title = "Wanted: TCG card $suffix"; description = 'Looking for a sealed card'; category = 'TCG'; budget = 30000
    endsAt = [DateTimeOffset]::UtcNow.AddMinutes(5).ToString('o')
} $buyerToken
$offer = Invoke-CollectBid POST "/api/auctions/$($reverse.id)/offers" @{
    productId = $secondProduct.id; amount = 25000; note = 'Shipping included'
} $sellerToken ([guid]::NewGuid().ToString())
$selected = Invoke-CollectBid POST "/api/auctions/$($reverse.id)/offers/$($offer.id)/accept" $null $buyerToken
if ($selected.status -ne 'CLOSED') { throw 'Reverse auction did not close.' }
Write-Host "Reverse auction $($reverse.id): buyer selected the offer."

Write-Host 'Waiting for automatic forward-auction settlement...'
$waitUntil = $auctionEnd.AddSeconds(20)
do {
    $current = Invoke-CollectBid GET "/api/auctions/$($auction.id)"
    if ($current.status -eq 'CLOSED') { break }
    if ([DateTimeOffset]::UtcNow -gt $waitUntil) { throw 'Automatic closing timed out. Check the scheduler configuration and logs.' }
    Start-Sleep -Milliseconds 500
} while ($true)

$orders = Invoke-CollectBid GET '/api/orders' $null $buyerToken
if ($orders.totalElements -ne 2) { throw "Expected two orders; found $($orders.totalElements)." }
Write-Host 'Demo passed: both auctions created exactly one order.'
$orders.items | Select-Object id, auctionId, productTitle, amount, status | Format-Table
$notifications = Invoke-CollectBid GET '/api/notifications' $null $buyerToken
Write-Host "Buyer notifications: $($notifications.totalElements)"
Invoke-CollectBid POST '/api/auth/logout' $null $sellerToken | Out-Null
Invoke-CollectBid POST '/api/auth/logout' $null $buyerToken | Out-Null
Write-Host "Demo accounts: $sellerEmail / $buyerEmail (tokens revoked; demo data retained)."
