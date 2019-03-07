.PHONY: install-deps
install-deps:
	go get -u github.com/golang/dep/cmd/dep
	dep ensure

.PHONY: test
test:
	go test -v ./commands/
	go test -v ./entities/
	go test -v ./worker/
	go test -v ./tests/
