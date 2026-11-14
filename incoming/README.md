# incoming/

Put a project `.zip` here and push to `main`.

The **Incoming → Build → Release** workflow will:

1. take the newest zip (by version order),
2. replace the whole project with its contents (the folder `incoming/` and `.github/workflows/` are kept),
3. build the release APK,
4. commit the new project state and delete the zip,
5. publish a GitHub Release `vX.Y` with the APK, the source zip and checksums.

The version is read from the `VERSION` file inside the zip (fallback: the number in the zip file name).
