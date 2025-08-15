!#bin/sh

echo "Compiling source files"
javac -sourcepath src src/Main.java -d out

echo "Copying resources"
cp resources/packages.txt out/

echo "Running"
java -cp out Main "$@"
