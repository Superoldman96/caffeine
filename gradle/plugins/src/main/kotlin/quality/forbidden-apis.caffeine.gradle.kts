import de.thetaphi.forbiddenapis.gradle.CheckForbiddenApis

plugins {
  `jvm-ecosystem`
  id("de.thetaphi.forbiddenapis")
}

forbiddenApis {
  ignoreSignaturesOfMissingClasses = true
}

tasks.withType<CheckForbiddenApis>().configureEach {
  forbiddenApis.failOnMissingClasses = !java.toolchain.languageVersion.get()
    .canCompileOrRun(JavaVersion.current().majorVersion.toInt())
  val isEnabled = providers.systemProperty("forbiddenApis")
  incompatibleWithConfigurationCache()
  onlyIf { isEnabled.isPresent }
}
