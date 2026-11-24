// Backend projektu INPUT – osobny projekt Gradle (otwierany np. w IntelliJ IDEA).
//  - server    – serwer centralny (REST API + baza SQLite), z którym łączy się aplikacja i paczkomaty
//  - paczkomat – program uruchamiany na Raspberry Pi w fizycznym paczkomacie
rootProject.name = "input-backend"

include("server", "paczkomat")
