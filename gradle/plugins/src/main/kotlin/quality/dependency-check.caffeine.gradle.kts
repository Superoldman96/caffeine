plugins {
  id("org.owasp.dependencycheck")
}

dependencyCheck {
  failOnError = false
  scanBuildEnv = true
  formats = listOf("HTML", "SARIF")
  nvd.apiKey = providers.environmentVariable("NVD_API_KEY").orNull
}
