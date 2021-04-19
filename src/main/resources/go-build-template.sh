set -x
set -e

export PROTOS_SRC_DIR={{{PROTO_SRC_DIR}}}
export INTERFACE_VERSION={{INTERFACE_VERSION}}
export INTERFACE_NAME={{INTERFACE_NAME}}
export PWD=$(pwd)


export PROXY_SRC_OUT=target/proxy_src

cd $PROXY_SRC_OUT
go mod tidy
go install \
 github.com/grpc-ecosystem/grpc-gateway/v2/protoc-gen-grpc-gateway \
 github.com/grpc-ecosystem/grpc-gateway/v2/protoc-gen-openapiv2 \
 google.golang.org/protobuf/cmd/protoc-gen-go \
 google.golang.org/grpc/cmd/protoc-gen-go-grpc
go build -i -o ${INTERFACE_NAME}-rest-proxy-${INTERFACE_VERSION}
