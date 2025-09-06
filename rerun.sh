!#bin/sh

echo "Compiling source files"
javac -sourcepath src src/Main.java -d out

if [ "$?" -ne 0 ]; then
    echo "Failed to compile"
    exit 1
fi

echo "Copying resources"
cp resources/packages.txt out/

echo "Running"
java -cp out Main "$@"
