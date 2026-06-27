UPDATE company_members SET role = 'REVIEWER' WHERE role = 'APPROVER';

ALTER TABLE documents DROP CONSTRAINT fk_documents_company;
ALTER TABLE documents ADD CONSTRAINT fk_documents_company
    FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE;

ALTER TABLE sops DROP CONSTRAINT fk_sops_company;
ALTER TABLE sops ADD CONSTRAINT fk_sops_company
    FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE;
