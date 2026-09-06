package com.inditex.similarproducts;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Enforces the dependency direction of the hexagonal architecture, so the boundaries described in
 * the README stay verified by the build instead of relying on review discipline.
 */
class HexagonalArchitectureTest {

    private static JavaClasses productionClasses;

    @BeforeAll
    static void importProductionClasses() {
        productionClasses = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.inditex.similarproducts");
    }

    @Test
    void domainDependsOnlyOnTheJdk() {
        classes()
                .that().resideInAPackage("..domain..")
                .should().onlyDependOnClassesThat()
                .resideInAnyPackage("..domain..", "java..")
                .check(productionClasses);
    }

    @Test
    void applicationDependsOnNeitherSpringNorInfrastructure() {
        noClasses()
                .that().resideInAPackage("..application..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("org.springframework..", "..infrastructure..")
                .check(productionClasses);
    }

    @Test
    void transportAndCachingDetailsStayInInfrastructure() {
        noClasses()
                .that().resideOutsideOfPackage("..infrastructure..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("org.springframework.web..", "com.github.benmanes.caffeine..", "reactor.netty..")
                .check(productionClasses);
    }
}
