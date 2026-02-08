@echo off
echo Downloading Android Platform Tools...
powershell -Command "Invoke-WebRequest -Uri 'https://dl.google.com/android/repository/platform-tools-latest-windows.zip' -OutFile 'c:\Users\simpl\platform-tools.zip'"
echo Download complete.
echo Extracting...
powershell -Command "Expand-Archive -Path 'c:\Users\simpl\platform-tools.zip' -DestinationPath 'c:\Users\simpl\adb' -Force"
echo Extraction complete.
echo ADB should be at c:\Users\simpl\adb\platform-tools\adb.exe
if exist c:\Users\simpl\adb\platform-tools\adb.exe (
    echo ADB Found!
    c:\Users\simpl\adb\platform-tools\adb.exe version
) else (
    echo ADB Not Found. Something went wrong.
)
pause
