CREATE TYPE profile_type_enum AS ENUM ('HOUSEHOLD', 'ADMIN', 'COMPANY', 'STORE');
CREATE TYPE unit_of_measure_enum AS ENUM ('g', 'kg', 'ml', 'l', 'unit');
CREATE TYPE payment_status_enum AS ENUM ('PENDING', 'PAID', 'OVERDUE', 'CANCELLED');
CREATE TYPE profile_status_enum AS ENUM ('ACTIVE', 'INACTIVE', 'BLOCKED');
CREATE TYPE auth_provider_enum AS ENUM ('GOOGLE', 'MICROSOFT', 'PASSWORD');

CREATE TABLE category (
    id SERIAL PRIMARY KEY,
    category_name VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE food (
    id SERIAL PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    category_id INTEGER NOT NULL REFERENCES category(id) ON UPDATE CASCADE ON DELETE RESTRICT,
    product_brand VARCHAR(100),
    package_quantity DECIMAL(10,2) NOT NULL CHECK (package_quantity > 0),
    unit_of_measure unit_of_measure_enum NOT NULL,
    CONSTRAINT uq_food UNIQUE (name, product_brand, package_quantity, unit_of_measure)
);

CREATE TABLE address (
    id SERIAL PRIMARY KEY,
    neighborhood VARCHAR(100) NOT NULL,
    street VARCHAR(150) NOT NULL,
    number VARCHAR(10) NOT NULL,
    cep CHAR(8) NOT NULL CHECK (cep ~ '^\d{8}$'),
    city VARCHAR(100) NOT NULL,
    state CHAR(2) NOT NULL CHECK (state ~ '^[A-Z]{2}$')
);

CREATE TABLE profile (
    id SERIAL PRIMARY KEY,
    address_id INTEGER NOT NULL REFERENCES address(id) ON DELETE RESTRICT,
    email VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(150) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    type profile_type_enum NOT NULL,
    profile_image_url VARCHAR(500),
    status profile_status_enum NOT NULL DEFAULT 'ACTIVE'
);

CREATE TABLE profile_phone (
    profile_id INTEGER NOT NULL REFERENCES profile(id) ON DELETE CASCADE,
    phone VARCHAR(16) NOT NULL CHECK (phone ~ '^\+[1-9][0-9]{7,14}$'),
    CONSTRAINT pk_profile_phone PRIMARY KEY (profile_id, phone)
);

CREATE TABLE auth_method (
    id SERIAL PRIMARY KEY,
    profile_id INTEGER NOT NULL REFERENCES profile(id) ON DELETE CASCADE,
    provider auth_provider_enum NOT NULL,
    credential VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_account_provider UNIQUE (profile_id, provider)
);

CREATE UNIQUE INDEX uq_external_auth_identity
    ON auth_method (provider, credential)
    WHERE provider IN ('GOOGLE', 'MICROSOFT');

CREATE TABLE pantry_item (
    id SERIAL PRIMARY KEY,
    food_id INTEGER NOT NULL REFERENCES food(id),
    profile_id INTEGER NOT NULL REFERENCES profile(id) ON DELETE CASCADE,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    expiry_date DATE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE pantry_product_setting (
    id SERIAL PRIMARY KEY,
    food_id INTEGER NOT NULL REFERENCES food(id) ON DELETE CASCADE,
    profile_id INTEGER NOT NULL REFERENCES profile(id) ON DELETE CASCADE,
    minimum_quantity INTEGER NOT NULL DEFAULT 0 CHECK (minimum_quantity >= 0),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_pantry_product_setting UNIQUE (profile_id, food_id)
);

CREATE TABLE plan (
    id SERIAL PRIMARY KEY,
    plan_price DECIMAL(10,2) NOT NULL CHECK (plan_price >= 0),
    plan_name VARCHAR(80) NOT NULL UNIQUE,
    store_limit INTEGER NOT NULL CHECK (store_limit > 0),
    description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE company (
    id SERIAL PRIMARY KEY,
    plan_id INTEGER NOT NULL REFERENCES plan(id),
    cnpj CHAR(14) NOT NULL UNIQUE CHECK (cnpj ~ '^\d{14}$'),
    profile_id INTEGER NOT NULL UNIQUE REFERENCES profile(id)
);

CREATE TABLE store (
    id SERIAL PRIMARY KEY,
    cnpj CHAR(14) NOT NULL UNIQUE CHECK (cnpj ~ '^\d{14}$'),
    company_id INTEGER NOT NULL REFERENCES company(id) ON DELETE CASCADE,
    profile_id INTEGER NOT NULL UNIQUE REFERENCES profile(id)
);

CREATE TABLE payment (
    id SERIAL PRIMARY KEY,
    due_date DATE NOT NULL,
    billing_period_start DATE NOT NULL,
    billing_period_end DATE NOT NULL,
    payment_status payment_status_enum NOT NULL,
    company_id INTEGER NOT NULL REFERENCES company(id),
    plan_id INTEGER NOT NULL REFERENCES plan(id),
    amount DECIMAL(10,2) NOT NULL CHECK (amount >= 0),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    paid_at TIMESTAMP,
    CONSTRAINT ck_payment_billing_period CHECK (billing_period_end >= billing_period_start)
);

CREATE OR REPLACE FUNCTION validate_profile_reference_type() RETURNS TRIGGER AS $$
DECLARE
    actual_type profile_type_enum;
    expected_type profile_type_enum;
BEGIN
    SELECT type
      INTO actual_type
      FROM profile
     WHERE id = NEW.profile_id;

    IF actual_type IS NULL THEN
        RAISE EXCEPTION 'profile % does not exist', NEW.profile_id;
    END IF;

    expected_type := TG_ARGV[0]::profile_type_enum;

    IF actual_type <> expected_type THEN
        RAISE EXCEPTION
            'profile_id must reference a profile of type %, received %',
            expected_type,
            actual_type;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_pantry_household_type
    BEFORE INSERT OR UPDATE OF profile_id ON pantry_item
    FOR EACH ROW EXECUTE FUNCTION validate_profile_reference_type('HOUSEHOLD');

CREATE TRIGGER trg_pantry_product_setting_household_type
    BEFORE INSERT OR UPDATE OF profile_id ON pantry_product_setting
    FOR EACH ROW EXECUTE FUNCTION validate_profile_reference_type('HOUSEHOLD');

CREATE TRIGGER trg_company_profile_type
    BEFORE INSERT OR UPDATE OF profile_id ON company
    FOR EACH ROW EXECUTE FUNCTION validate_profile_reference_type('COMPANY');

CREATE TRIGGER trg_store_profile_type
    BEFORE INSERT OR UPDATE OF profile_id ON store
    FOR EACH ROW EXECUTE FUNCTION validate_profile_reference_type('STORE');

CREATE OR REPLACE FUNCTION prevent_profile_type_change() RETURNS TRIGGER AS $$
BEGIN
    IF OLD.type IS DISTINCT FROM NEW.type THEN
        RAISE EXCEPTION 'profile type cannot be changed after creation';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_profile_type_lock
    BEFORE UPDATE ON profile
    FOR EACH ROW EXECUTE FUNCTION prevent_profile_type_change();
