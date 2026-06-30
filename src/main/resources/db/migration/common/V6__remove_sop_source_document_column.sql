DROP INDEX IF EXISTS idx_sops_source_document_id;

ALTER TABLE sops DROP CONSTRAINT IF EXISTS fk_sops_source_document;

ALTER TABLE sops DROP COLUMN IF EXISTS source_document_id;
