# Clojure Vector Database

A vector database implementation in Clojure, allowing for efficient similarity search and vector operations.

## Features

- Fast Vector Storage: Efficiently store and retrieve embedding vectors using DuckDB as the backend
- Vector storage and retrieval with multiple search methods:
  - Exact search (brute force)
  - Approximate search (random sampling)
  - LSH (Locality Sensitive Hashing) for faster approximate search
  - HNSW (Hierarchical Navigable Small World)
- In-memory and persistent storage options
- Web-based UI for management and search
- REST API for programmatic access
- Efficient vector caching with LRU cache eviction policy
- Performance metrics tracking
- Simple URL-based API: Easily search using just URL parameters
- Agent-Ready: Simple HTTP endpoints make it easy to integrate with AI agents and tools

## About the Database

The Clojure Vector Database provides:

- Fast in-memory vector storage with disk persistence
- Efficient caching system
- Multiple similarity metrics (cosine, euclidean, dot product)
- Partition-based storage for scalability
- LSH indexing for faster approximate searches

## Prerequisites

- [Clojure](https://clojure.org/guides/getting_started) 1.11 or later
- [Leiningen](https://leiningen.org/) 2.9 or later
- Java 11 or later

## Installation

Clone the repository:

```
git clone https://github.com/yourusername/clojure-vectordb.git
cd clojure-vectordb
```

## Usage

### Starting the Server

Start the server with Leiningen:

```
lein run
```

This will start the server on port 3000 by default.

### Running Examples

The project includes example code in the `examples` directory. To run the OpenAI example:

```
lein run -m openai
```

Note: You'll need to set your OpenAI API key as an environment variable:
```bash
export OPENAI_API_KEY="your-api-key-here"
```

### Configuration

Set the following environment variables to customize behavior:

- `PORT`: The HTTP port (default: 3000)
- `VECTOR_DB_PATH`: Path to the DuckDB database file (default: in-memory)
- `ENABLE_CSRF`: Enable CSRF protection, set to "true" to enable (default: disabled)

Example:

```
PORT=8080 VECTOR_DB_PATH="data/vectors.db" ENABLE_CSRF=true lein run
```

### Security

#### CSRF Protection

By default, CSRF (Cross-Site Request Forgery) protection is disabled to allow easier API and form usage. 

When CSRF protection is enabled:
- All form submissions must include a valid anti-forgery token
- The token is automatically included in forms rendered by the UI
- API calls may fail if they don't include the proper token

To enable CSRF protection, set the `ENABLE_CSRF` environment variable to "true":

```
ENABLE_CSRF=true lein run
```

Or when using Docker:

```
docker run -p 3000:3000 -e ENABLE_CSRF=true clojure-vectordb
```

### Web Interface

Access the web interface at http://localhost:3000/

The web interface allows you to:
- Search for similar documents
- Add new documents
- Delete documents
- View database statistics

### Simple URL-based Search

For easy integration with other tools, you can use the following URL pattern:

```
http://localhost:3000/api/search_text?query=your_search_text
```

Example:

```
http://localhost:3000/api/search_text?query=python
```

Additional parameters:

- `k`: Number of results to return (default: 3)
- `method`: Search method to use (default: "hnsw", options: "exact", "approximate", "lsh", "hnsw")

Example with all parameters:

```
http://localhost:3000/api/search_text?query=python&k=5&method=exact
```

### REST API

The application provides a comprehensive REST API for programmatic access:

#### Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/` | GET | Get API info |
| `/api/vectors` | GET | Get all vectors |
| `/api/search_text` | GET | Search by text query |
| `/api/vectors/:key` | POST | Insert a vector |
| `/api/vectors/:key` | DELETE | Delete a vector |
| `/api/search` | POST | Search by vector |
| `/api/metrics` | GET | Get performance metrics |

#### Example Requests

Search by text:
```
GET /api/search_text?query=machine%20learning&k=3&method=exact
```

Insert a vector:
```
POST /api/vectors/doc123
Content-Type: application/json

{
  "vector": [0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0]
}
```

Search by vector:
```
POST /api/search
Content-Type: application/json

{
  "query_vector": [0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0],
  "k": 5,
  "method": "lsh"
}
```

## Building an Uberjar

Create a standalone JAR with all dependencies:

```
lein uberjar
```

Run the uberjar:

```
java -jar target/uberjar/vectordb-0.1.0-SNAPSHOT-standalone.jar
```

## Running with Docker

Build the Docker image:

```
docker build -t clojure-vectordb .
```

Run the container:

```
docker run -p 3000:3000 clojure-vectordb
```

Then access the UI at http://localhost:3000 or use the API endpoints.

If port 3000 is already in use, you can map to a different port:

```
docker run -p 3001:3000 clojure-vectordb
```

Then access the UI at http://localhost:3001.

To stop a running container:

```
docker ps
docker stop CONTAINER_ID
```

## Troubleshooting

### Port Already in Use

If you see an error like "port already in use" when starting the server:

1. Find the process using the port:
   ```
   lsof -i :3000
   ```

2. Stop the running container or process:
   ```
   docker stop CONTAINER_ID
   ```
   
3. Or use a different port:
   ```
   docker run -p 3001:3000 clojure-vectordb
   ```

### Database Connection Issues

If you experience database connection issues:

1. Make sure the database path is accessible and writable
2. Check the logs for specific DuckDB error messages
3. Try using an in-memory database for testing by not setting the `VECTOR_DB_PATH` variable

## Developer Tools

The `core.clj` file contains development tools in a `comment` block that can be evaluated in a REPL:

```clojure
(def server (start-server 3000))
(stop-server server)

;; Initialize fresh sample data 
(api/init-sample-data @vector-db)

;; Check existing data
(model/get-all-keys @vector-db)

;; Get database stats
(api/get-database-stats @vector-db)

;; Search example
(let [query "machine learning"
      query-vector (vectordb.embedding/simple-embedding query)]
  (model/search @vector-db query-vector 3))
```

## License
Created by Kirill Ostapenko

Copyright © 2023 

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

