@echo off

echo Copying resources
copy /Y resources\packages.txt out

echo Running
java -cp out Main %*

@echo on
