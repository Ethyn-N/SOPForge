CREATE SCHEMA IF NOT EXISTS extensions;

CREATE EXTENSION IF NOT EXISTS vector
    WITH SCHEMA extensions;

-- noinspection SqlResolve
ALTER TABLE document_chunks
    ADD COLUMN embedding extensions.vector(1024);

-- noinspection SqlResolve
CREATE INDEX idx_document_chunks_embedding_hnsw
    ON document_chunks
    USING hnsw (embedding extensions.vector_cosine_ops);
