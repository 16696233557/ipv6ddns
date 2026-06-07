@rem Gradle wrapper for Windows
@echo off
setlocal
set DIR=%~dp0
set CLASSPATH=%DIR%gradle\wrapper\gradle-wrapper.jar
if defined JAVA_HOME (
    set JAVA_CMD=%JAVA_HOME%\bin\java.exe
) else (
    set JAVA_CMD=java
)
"%JAVA_CMD%" %JAVA_OPTS% %GRADLE_OPTS% ^
    -classpath "%CLASSPATH%" ^
    org.gradle.wrapper.GradleWrapperMain %*
endlocal
