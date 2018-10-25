package utils

import (
	"fmt"
	"github.com/golang/protobuf/proto"
	"time"
)

const DefaultTestTimeout = 5 * time.Second

// RpcTestMsg implements the gomock.Matcher interface
type RpcTestMsg struct {
	Msg proto.Message
}

func (r *RpcTestMsg) Matches(msg interface{}) bool {
	m, ok := msg.(proto.Message)
	if !ok {
		return false
	}
	return proto.Equal(m, r.Msg)
}

func (r *RpcTestMsg) String() string {
	return fmt.Sprintf("is %s", r.Msg)
}
