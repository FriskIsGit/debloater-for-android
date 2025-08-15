@echo off

echo Compiling source files
javac -sourcepath src src/Main.java -d out

echo Copying resources
copy /Y resources\packages.txt out

echo Running
java -cp out Main %*

@echo on
