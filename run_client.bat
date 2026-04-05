@echo off
set "JAVA_HOME=I:\ir_auto\jdk8\jdk8u482-b08"
set "PATH=%JAVA_HOME%\bin;%PATH%"
cd /d "i:\ir_auto\MC-1.12.2-ForgeMDK-master"
gradlew.bat runClient
