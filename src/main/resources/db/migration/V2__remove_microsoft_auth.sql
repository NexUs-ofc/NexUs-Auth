DELETE FROM auth_method
WHERE provider = 'MICROSOFT';

DROP INDEX IF EXISTS uq_external_auth_identity;

ALTER TYPE auth_provider_enum RENAME TO auth_provider_enum_old;

CREATE TYPE auth_provider_enum AS ENUM (
    'GOOGLE',
    'PASSWORD'
);

ALTER TABLE auth_method
    ALTER COLUMN provider TYPE auth_provider_enum
    USING provider::text::auth_provider_enum;

DROP TYPE auth_provider_enum_old;

CREATE UNIQUE INDEX uq_external_auth_identity
ON auth_method (provider, credential)
WHERE provider = 'GOOGLE';