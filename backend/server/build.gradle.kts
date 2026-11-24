plugins {
    application
}

repositories {
    mavenCentral()
}

dependencies {
    // Obsługa formatu JSON (te same klasy JSONObject/JSONArray, które są wbudowane w Androida).
    implementation("org.json:json:20240303")
    // Sterownik bazy danych SQLite – cała baza to jeden plik input.db.
    implementation("org.xerial:sqlite-jdbc:3.46.1.3")
    // Wycisza ostrzeżenia biblioteki logującej używanej przez sterownik SQLite.
    runtimeOnly("org.slf4j:slf4j-nop:1.7.36")
}

java {
    // Java 17 jest dostępna w Raspberry Pi OS, więc serwer można uruchomić również na malince.
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<JavaCompile> {
    // Polskie znaki w kodzie źródłowym – bez tego Windows kompiluje w cp1250.
    options.encoding = "UTF-8"
}

application {
    mainClass.set("pl.input.server.Main")
}

// Komenda "gradlew :server:jar" tworzy jeden plik server.jar ze wszystkimi bibliotekami.
// Uruchomienie: java -jar server/build/libs/server.jar
tasks.jar {
    manifest {
        attributes["Main-Class"] = "pl.input.server.Main"
    }
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
