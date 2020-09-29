plugins {
  kotlin("jvm") version "1.4.10"
}

repositories {
  jcenter()
}

val jacksonVersion = "2.10.1"

dependencies {
  implementation(kotlin("stdlib-jdk8"))
  implementation(kotlin("reflect"))
  implementation("org.apache.httpcomponents:httpclient:4.5.10")
  implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
  implementation("com.fasterxml.jackson.module:jackson-module-parameter-names:$jacksonVersion")
  implementation("com.fasterxml.jackson.module:jackson-modules-java8:$jacksonVersion")
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
}
