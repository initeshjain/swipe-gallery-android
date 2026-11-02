# Swipe Gallery

A native Android gallery built with Kotlin and Jetpack Compose. Browse photos and device albums, swipe right to keep and left to move photos into the app's recoverable bin, restore items, or permanently delete them.

Photos stay on device. Swipe Gallery reads image metadata through Android MediaStore and stores only the bin's photo IDs in app preferences. The bin is app-local and does not move files into Android's system trash.

See [BUILD_APK.md](BUILD_APK.md) for the debug, release, install, and size inspection commands.
