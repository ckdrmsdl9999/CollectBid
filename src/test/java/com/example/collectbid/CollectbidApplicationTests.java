package com.example.collectbid;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/** Fast architecture checks: no running database is required for mvnw test. */
class CollectbidApplicationTests {
    private static final String ROOT = "com.example.collectbid.";
    private static final Set<String> MODULES = Set.of("member", "product", "auction", "order", "notification", "global");

    @Test
    void modulesOnlyAccessOtherModulesThroughPublishedApis() {
        var classes = new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages(ROOT.substring(0, ROOT.length() - 1));
        for (var source : classes) {
            String sourceModule = module(source.getName());
            for (var dependency : source.getDirectDependenciesFromSelf()) {
                String target = dependency.getTargetClass().getName();
                String targetModule = module(target);
                if (!MODULES.contains(sourceModule) || !MODULES.contains(targetModule) || sourceModule.equals(targetModule)) continue;
                assertThat(targetModule.equals("global") || target.startsWith(ROOT + targetModule + ".api."))
                        .as("Only public module contracts may cross boundaries: %s", dependency.getDescription()).isTrue();
                assertThat(sourceModule).as("Global infrastructure must not depend on business modules").isNotEqualTo("global");
            }
        }
        slices().matching("com.example.collectbid.(*)..").should().beFreeOfCycles().check(classes);
    }

    private String module(String className) {
        if (!className.startsWith(ROOT)) return "";
        return className.substring(ROOT.length()).split("\\.")[0];
    }
}
