-- The multi-tenant foundation: tenants with their branding and branches,
-- users (tenant users and super-users), sessions, and the per-tenant AI
-- access with its call log. Everything a tenant owns cascades with it, so
-- deleting a tenant on the Mandanten page is one statement and nothing is
-- left behind to point at a tenant that is gone.

-- A tenant is one customer. The Kennung (slug) is what a person types on the
-- login page to say which tenant they belong to, so the same username may
-- exist in several tenants and nobody's customer list has to be shown before
-- a login.
CREATE TABLE tenants (
    id            UUID        PRIMARY KEY,
    name          TEXT        NOT NULL,
    slug          TEXT        NOT NULL,
    website       TEXT,
    -- How the sidebar brands the tenant: small logo beside the name, or one
    -- large logo filling the whole brand area.
    logo_display  TEXT        NOT NULL DEFAULT 'WITH_NAME'
        CHECK (logo_display IN ('WITH_NAME', 'LOGO_ONLY')),
    -- The tenant's brand color (hex #RRGGBB). It becomes the app's default
    -- primary color for the tenant's users; a user's own theme choice still wins.
    primary_color TEXT,
    created_at    TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX tenants_slug_key ON tenants (slug);

-- The logo lives in its own table: the tenant row travels with almost every
-- request, the image must not.
CREATE TABLE tenant_logos (
    id           UUID        PRIMARY KEY,
    tenant_id    UUID        NOT NULL UNIQUE REFERENCES tenants (id) ON DELETE CASCADE,
    image        BYTEA       NOT NULL,
    content_type TEXT        NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL
);

-- A company consists of a headquarters and any number of branches
-- (Filialen). Address and contact data live here; the tenant keeps its
-- identity and branding.
CREATE TABLE branches (
    id              UUID        PRIMARY KEY,
    tenant_id       UUID        NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    name            TEXT        NOT NULL,
    is_headquarters BOOLEAN     NOT NULL,
    street          TEXT,
    postal_code     TEXT,
    city            TEXT,
    country         TEXT,
    phone           TEXT,
    fax             TEXT,
    email           TEXT,
    created_at      TIMESTAMPTZ NOT NULL
);

CREATE INDEX branches_tenant_id_idx ON branches (tenant_id);

-- Exactly one headquarters per tenant.
CREATE UNIQUE INDEX branches_headquarters_key ON branches (tenant_id) WHERE is_headquarters;

-- The name identifies the site in dropdowns, so it is unique per tenant.
CREATE UNIQUE INDEX branches_name_key ON branches (tenant_id, LOWER(name));

-- A person who can sign in. A super-user belongs to no tenant (tenant_id is
-- null) and carries the SUPERUSER role.
CREATE TABLE users (
    id            UUID        PRIMARY KEY,
    tenant_id     UUID        REFERENCES tenants (id) ON DELETE CASCADE,
    -- The login name: any string, unique within its tenant.
    username      TEXT        NOT NULL,
    first_name    TEXT        NOT NULL,
    last_name     TEXT        NOT NULL,
    birth_date    DATE,
    joined_at     DATE,
    -- Deleting a branch only unsets the assignment, it never blocks on
    -- assigned users.
    branch_id     UUID        REFERENCES branches (id) ON DELETE SET NULL,
    -- Contact data, not the login.
    email         TEXT,
    phone         TEXT,
    fax           TEXT,
    position      TEXT,
    password_hash TEXT        NOT NULL,
    role          TEXT        NOT NULL CHECK (role IN ('ADMIN', 'USER', 'SUPERUSER')),
    active        BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL
);

CREATE INDEX users_tenant_id_idx ON users (tenant_id);

-- Usernames are unique within a tenant, and super-users' among each other.
CREATE UNIQUE INDEX users_username_key ON users (tenant_id, LOWER(username));
CREATE UNIQUE INDEX users_superuser_username_key ON users (LOWER(username)) WHERE tenant_id IS NULL;

-- Profile pictures live in their own table, not as a column on users: the user
-- row is loaded on practically every request (auth, tenant scoping), and a
-- multi-megabyte image must not travel along. Only the avatar endpoint reads it.
CREATE TABLE user_avatars (
    id           UUID        PRIMARY KEY,
    user_id      UUID        NOT NULL UNIQUE REFERENCES users (id) ON DELETE CASCADE,
    image        BYTEA       NOT NULL,
    content_type TEXT        NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL
);

-- Verbatim copy of schema-postgresql.sql from spring-session-jdbc 4.1.0.
-- Flyway owns the schema, so Spring Session's own initializer is disabled
-- (spring.session.jdbc.initialize-schema=never). Sessions live in the
-- database so they survive restarts and work across multiple instances.
CREATE TABLE SPRING_SESSION (
	PRIMARY_ID CHAR(36) NOT NULL,
	SESSION_ID CHAR(36) NOT NULL,
	CREATION_TIME BIGINT NOT NULL,
	LAST_ACCESS_TIME BIGINT NOT NULL,
	MAX_INACTIVE_INTERVAL INT NOT NULL,
	EXPIRY_TIME BIGINT NOT NULL,
	PRINCIPAL_NAME VARCHAR(100),
	CONSTRAINT SPRING_SESSION_PK PRIMARY KEY (PRIMARY_ID)
);

CREATE UNIQUE INDEX SPRING_SESSION_IX1 ON SPRING_SESSION (SESSION_ID);
CREATE INDEX SPRING_SESSION_IX2 ON SPRING_SESSION (EXPIRY_TIME);
CREATE INDEX SPRING_SESSION_IX3 ON SPRING_SESSION (PRINCIPAL_NAME);

CREATE TABLE SPRING_SESSION_ATTRIBUTES (
	SESSION_PRIMARY_ID CHAR(36) NOT NULL,
	ATTRIBUTE_NAME VARCHAR(200) NOT NULL,
	ATTRIBUTE_BYTES BYTEA NOT NULL,
	CONSTRAINT SPRING_SESSION_ATTRIBUTES_PK PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME),
	CONSTRAINT SPRING_SESSION_ATTRIBUTES_FK FOREIGN KEY (SESSION_PRIMARY_ID) REFERENCES SPRING_SESSION(PRIMARY_ID) ON DELETE CASCADE
);

-- A tenant's own Anthropic access. Optional: without a key the platform's own
-- credentials are used, which is what every tenant starts on.
CREATE TABLE tenant_ai_settings (
    id         UUID        PRIMARY KEY,
    tenant_id  UUID        NOT NULL UNIQUE REFERENCES tenants (id) ON DELETE CASCADE,
    -- Encrypted by the application before it ever reaches this column; the
    -- stored value carries the marker that says so.
    api_key    TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

-- Every call to the model, one row each, with the tokens it used and what
-- they were worth. The amount is computed when the call is recorded, from the
-- price table in the configuration, and kept as it was: a later price change
-- does not re-value old calls. Null where the model had no price at the time.
CREATE TABLE ai_calls (
    id            UUID           PRIMARY KEY,
    tenant_id     UUID           NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    kind          TEXT           NOT NULL,
    model         TEXT           NOT NULL,
    input_tokens  BIGINT         NOT NULL,
    output_tokens BIGINT         NOT NULL,
    cost_usd      NUMERIC(12, 6),
    called_at     TIMESTAMPTZ    NOT NULL
);

-- The cost page asks for a tenant's calls over a stretch of time, nothing else.
CREATE INDEX ai_calls_tenant_id_called_at_idx ON ai_calls (tenant_id, called_at);
