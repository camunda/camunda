@echo on
go build -o packager.exe ./cmd/packager
.\packager.exe package
