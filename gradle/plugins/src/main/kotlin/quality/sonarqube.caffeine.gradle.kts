plugins {
  id("org.sonarqube")
  id("coverage.caffeine")
}

sonarqube {
  properties {
    property("sonar.organization", "caffeine")
    property("sonar.host.url", "https://sonarcloud.io")
    property("sonar.cpd.exclusions", "**/simulator/**")
    property("sonar.coverage.exclusions", "**/simulator/**")
    property("sonar.token", providers.environmentVariable("SONAR_TOKEN").orElse(""))
    property("sonar.coverage.jacoco.xmlReportPaths",
      file(layout.buildDirectory.file("reports/jacoco/jacocoFullReport/jacocoFullReport.xml")))
  }
}

val jacocoFullReport by tasks.existing
tasks.named("sonarqube").configure {
  dependsOn(jacocoFullReport)
}
