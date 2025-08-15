!#bin/sh

echo "Copying resources"
cp resources/packages.txt out/

echo "Running"
java -cp out Main "$@"
