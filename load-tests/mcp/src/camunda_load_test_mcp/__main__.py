from camunda_load_test_mcp.server import create_server


def main() -> None:
    server = create_server()
    server.run()


if __name__ == "__main__":
    main()
