CREATE TABLE sop_source_documents (
    sop_id BIGINT NOT NULL,
    document_id BIGINT NOT NULL,
    PRIMARY KEY (sop_id, document_id),
    CONSTRAINT fk_sop_source_documents_sop FOREIGN KEY (sop_id) REFERENCES sops(id) ON DELETE CASCADE,
    CONSTRAINT fk_sop_source_documents_document FOREIGN KEY (document_id) REFERENCES documents(id) ON DELETE CASCADE
);

INSERT INTO sop_source_documents (sop_id, document_id)
SELECT id, source_document_id
FROM sops;

CREATE INDEX idx_sop_source_documents_document_id ON sop_source_documents(document_id);
