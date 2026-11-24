plugins {
    application
}

repositories {
    mavenCentral()
}

dependencies {
    // Obsługa formatu JSON w odpowiedziach serwera.
    implementation("org.json:json:20240303")
}

java {
    // Java 17 jest dostępna w Raspberry Pi OS (sudo apt install openjdk-17-jre).
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

application {
    mainClass.set("pl.input.paczkomat.Main")
}

// "gradlew :paczkomat:run" – przekazujemy klawiaturę do programu, żeby dało się wpisywać kody.
tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}

// "gradlew :paczkomat:jar" tworzy jeden plik paczkomat.jar, który kopiujemy na Raspberry Pi.
// Uruchomienie: java -jar paczkomat.jar A1 http://ADRES_SERWERA:8080
tasks.jar {
    manifest {
        attributes["Main-Class"] = "pl.input.paczkomat.Main"
    }
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
