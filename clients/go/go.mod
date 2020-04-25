module github.com/zeebe-io/zeebe/clients/go

go 1.13

replace github.com/docker/docker => github.com/docker/engine v1.4.2-0.20191113042239-ea84732a7725

require (
	github.com/Microsoft/go-winio v0.4.14 // indirect
	github.com/containerd/containerd v1.3.2 // indirect
	github.com/containerd/continuity v0.0.0-20190827140505-75bee3e2ccb6 // indirect
	github.com/docker/distribution v2.7.1+incompatible // indirect
	github.com/docker/docker v1.13.1
	github.com/docker/go-connections v0.4.0
	github.com/docker/go-units v0.4.0 // indirect
	github.com/go-ozzo/ozzo-validation/v4 v4.1.0
	github.com/gogo/protobuf v1.3.1 // indirect
	github.com/golang/mock v1.4.3
	github.com/golang/protobuf v1.4.0
	github.com/google/go-cmp v0.4.0
	github.com/konsorten/go-windows-terminal-sequences v1.0.2 // indirect
	github.com/mitchellh/go-homedir v1.1.0
	github.com/sirupsen/logrus v1.4.2 // indirect
	github.com/spf13/cobra v1.0.0
	github.com/spf13/pflag v1.0.5 // indirect
	github.com/stretchr/testify v1.5.1
	github.com/testcontainers/testcontainers-go v0.5.1
	golang.org/x/net v0.0.0-20200425230154-ff2c4b7c35a0
	golang.org/x/oauth2 v0.0.0-20200107190931-bf48bf16ab8d
	golang.org/x/sync v0.0.0-20190911185100-cd5d95a43a6e // indirect
	google.golang.org/genproto v0.0.0-20191009194640-548a555dbc03 // indirect
	google.golang.org/grpc v1.29.1
	gopkg.in/yaml.v2 v2.2.8
)
