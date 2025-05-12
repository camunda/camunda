@echo on
go build -o packager .\cmd\packager
.\packager.exe package
