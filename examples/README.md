# Clojure Vector Database Examples

This directory contains examples of how to use the Clojure Vector Database library.

## Basic Usage

### As a Library

Add the dependency to your `project.clj`:
```clojure
[vectordb "0.1.0-SNAPSHOT"]
```

Basic example:
```clojure
(ns your-namespace
  (:require [vectordb.model :as vdb]))

;; Create a database (in-memory or with file)
(def db (vdb/new-vector-database "vectors.db"))

;; Add vectors
(vdb/insert db "key1" vector1)
(vdb/insert db "key2" vector2)

;; Search for similar vectors
(let [results (vdb/search db query-vector 5 "hnsw")]
  (doseq [[key similarity] (:results results)]
    (println key similarity)))
```

### Available Examples

1. `openai.clj` - Example using OpenAI embeddings
   - Shows how to use the library with OpenAI's text embeddings
   - Demonstrates batch processing of embeddings
   - Uses HNSW search method

### Search Methods

The library supports several search methods:
- `exact` - Brute force search (most accurate, slowest)
- `lsh` - Locality Sensitive Hashing (faster, approximate)
- `hnsw` - Hierarchical Navigable Small World (fast, approximate)
- `approximate` - Random sampling (fastest, least accurate)

### Database Options

1. In-memory database:
```clojure
(def db (vdb/new-vector-database))
```

2. Persistent database:
```clojure
(def db (vdb/new-vector-database "vectors.db"))
```

### Configuration

You can configure the database with additional parameters:
```clojure
(def db (vdb/new-vector-database 
         "vectors.db"    ; database path
         1000           ; chunk size
         (* 1024 1024)  ; max cache size (1MB)
         "cosine"))     ; similarity metric
```

## Running Examples

To run any example:
```bash
lein run -m example-name
```

For example:
```bash
lein run -m openai
```

## API Reference

### Core Functions

- `new-vector-database` - Create a new database instance
- `insert` - Add a vector to the database
- `search` - Search for similar vectors
- `delete` - Remove a vector from the database

### Search Parameters

The `search` function accepts:
- `query-vector` - The vector to search for
- `k` - Number of results to return
- `method` - Search method to use (optional, defaults to "exact")

### Return Values

Search results are returned in the format:
```clojure
{:results [[key1 similarity1]
           [key2 similarity2]
           ...]}
```

## Performance Considerations

1. For small datasets (< 1000 vectors), in-memory mode is fine
2. For larger datasets, use persistent storage
3. Choose search method based on your needs:
   - Use `exact` for accuracy
   - Use `hnsw` for speed
   - Use `lsh` for balanced performance
   - Use `approximate` for maximum speed

## Performance Optimization

### Embedding Generation

The main bottleneck is often the embedding generation. Here are several approaches to improve performance:

1. **Local Embedding Models**
   - Use local models like `sentence-transformers` instead of API calls
   - Much faster (milliseconds vs seconds)
   - No API costs
   - Example: Use `clojure-python` to call local Python models

2. **Batch Processing**
   - Always use batch API calls when possible
   - Reduces network overhead
   - Example:
   ```clojure
   ;; Instead of multiple single calls
   (doseq [text texts]
     (let [embedding (get-embedding text)]
       (insert db text embedding)))
   
   ;; Use batch processing
   (let [embeddings (get-embeddings-batch texts)]
     (doseq [[text embedding] (map vector texts embeddings)]
       (insert db text embedding)))
   ```

3. **Caching**
   - Cache embeddings locally
   - Store embeddings in a separate database
   - Reuse embeddings for similar queries

4. **Alternative Models**
   - Use faster/smaller models
   - Consider models like `all-MiniLM-L6-v2` instead of larger ones
   - Trade some accuracy for speed

### Database Optimization

1. **Indexing**
   - Use HNSW index for faster searches
   - Configure index parameters based on your needs
   - Example:
   ```clojure
   (def db (vdb/new-vector-database 
            "vectors.db"
            1000           ; chunk size
            (* 1024 1024)  ; cache size
            "cosine"       ; similarity metric
            {:hnsw {:M 16  ; number of connections
                   :ef 100 ; search accuracy
                   :ef-construction 200}})) ; build accuracy
   ```

2. **Chunking**
   - Use appropriate chunk sizes
   - Larger chunks = better compression but more memory
   - Smaller chunks = less memory but more I/O

3. **Caching**
   - Configure cache size based on your dataset
   - Monitor cache hit rates
   - Adjust cache size if needed

### Example Performance Numbers

With OpenAI API:
- Single embedding: ~1.2s
- Batch embedding (8 vectors): ~2.2s
- Search time: ~1.2s

With local model (sentence-transformers):
- Single embedding: ~50ms
- Batch embedding (8 vectors): ~200ms
- Search time: ~10ms

Choose the approach that best fits your needs:
- For production: Use local models
- For prototyping: API is fine
- For large datasets: Consider hybrid approach (local + API) 