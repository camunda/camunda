.PHONY: install-deps
install-deps:
	go get -u github.com/golang/dep/cmd/dep
	go get github.com/onsi/ginkgo/ginkgo
	dep ensure

.PHONY: test
test:
	go test -v ./commands/
	go test -v ./entities/
	go test -v ./worker/
	go test -v ./tests/
	cd tests/integration/ && ginkgo && cd ../..
