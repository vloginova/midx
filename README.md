MIDX
====================

MIDX (Mini Index) is a simple library for full-text file search. It uses the trigram-based index implemented on Kotlin
using coroutines and flows.

To run the demo:
```console
$ git clone https://github.com/vloginova/midx.git midx
$ cd midx
$ ./gradlew jar
$ java -jar build/libs/midx-1.0-SNAPSHOT.jar [--search-ignore-case] <files separated by space>
```