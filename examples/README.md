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

## Vector Database Examples

This directory contains example implementations using the vector database.

## TF-IDF Text Similarity Example

The `text_similarity.clj` example demonstrates how to use TF-IDF (Term Frequency-Inverse Document Frequency) for text similarity search without relying on external embedding services.

### Features

- Pure Clojure implementation of TF-IDF
- Cosine similarity for vector comparison
- Configurable relevance threshold
- Fast search using HNSW index
- No external dependencies for embeddings

### How it Works

1. **Text Processing**:
   ```clojure
   (defn tokenize [text]
     (-> text
         str/lower-case
         (str/split #"\s+")
         (->> (filter #(> (count %) 2))))
   ```
   - Converts text to lowercase
   - Splits into words
   - Filters out short words (length > 2)

2. **TF-IDF Calculation**:
   ```clojure
   (defn tf-idf [documents doc-freq n-docs all-terms]
     (map (fn [tokens]
            (let [tf (term-frequency tokens)]
              (reduce (fn [acc term]
                       (assoc acc term
                              (* (get tf term 0.0)
                                 (Math/log (/ n-docs
                                            (get doc-freq term 1))))))
                     {}
                     all-terms)))
          documents))
   ```
   - Term Frequency (TF): How often a term appears in a document
   - Inverse Document Frequency (IDF): How unique a term is across documents
   - Combined score: TF * log(N/DF) where N is total documents

3. **Vector Normalization**:
   ```clojure
   (defn normalize-vector [vec]
     (let [magnitude (Math/sqrt (reduce + (map #(* % %) vec)))]
       (if (zero? magnitude)
         vec
         (map #(/ % magnitude) vec))))
   ```
   - Normalizes vectors to unit length
   - Ensures consistent cosine similarity calculation

4. **Similarity Search**:
   ```clojure
   (defn example-with-tfidf []
     (def db (vdb/new-vector-database "vectors_tfidf.db"))
     ;; Insert documents
     ;; Search with query
     ;; Filter by threshold
   )
   ```
   - Stores normalized TF-IDF vectors in the database
   - Uses HNSW index for fast approximate search
   - Filters results by relevance threshold

### Usage

Run the example:
```bash
lein run -m text-similarity
```

Example output:
```
Adding texts using TF-IDF...
Total insert time: 11 ms

Query: What programming language is good for data analysis?
Search time: 3 ms
All results before filtering:
- Python is great for data science with similarity: 0.458
- Clojure is a functional programming language with similarity: 0.239
- JavaScript is widely used for web development with similarity: 0.089
- Rust is a systems programming language with similarity: 0.067

Filtered results (threshold: 0.1):
- Python is great for data science with similarity: 0.458
- Clojure is a functional programming language with similarity: 0.239
```

### Configuration

- `RELEVANCE-THRESHOLD`: Minimum similarity score (default: 0.1)
- Search method: "hnsw" for fast approximate search
- Number of results: 2 (configurable)

### Advantages

1. **No External Dependencies**: Works without OpenAI or other embedding services
2. **Fast Performance**: HNSW index for quick similarity search
3. **Interpretable**: TF-IDF scores are easy to understand
4. **Configurable**: Adjustable threshold and search parameters

### Limitations

1. **Semantic Understanding**: Less sophisticated than neural embeddings
2. **Term Matching**: Relies on exact term matches
3. **Vector Size**: Grows with vocabulary size

### When to Use

- Quick text similarity without external services
- Simple document matching
- When interpretability is important
- When fast performance is needed 