-- The habit tracker: every user keeps their own categories, habits and the
-- days they ticked off. Nothing here is shared — not with colleagues, not with
-- the admins — so everything hangs on the user, and through the user on the
-- tenant; deleting either takes it all along.

-- A way to group habits, e.g. "Ernährung" or "Sport". The name tells them
-- apart in the page's dropdown, so it is unique per user.
CREATE TABLE habit_categories (
    id         UUID        PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name       TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX habit_categories_name_key ON habit_categories (user_id, LOWER(name));

-- Something a user wants to do regularly: every day, or a number of days per
-- calendar week. The category is optional; deleting it leaves the habits
-- without one rather than taking them along. An archived habit is out of the
-- week grid but keeps its history.
CREATE TABLE habits (
    id            UUID        PRIMARY KEY,
    user_id       UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    category_id   UUID        REFERENCES habit_categories (id) ON DELETE SET NULL,
    name          TEXT        NOT NULL,
    frequency     TEXT        NOT NULL CHECK (frequency IN ('DAILY', 'WEEKLY')),
    weekly_target INTEGER,
    archived_at   TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL,
    -- A weekly habit says how many days; a daily one has nothing to say.
    CONSTRAINT habits_weekly_target_check CHECK (
        (frequency = 'DAILY' AND weekly_target IS NULL)
        OR (frequency = 'WEEKLY' AND weekly_target BETWEEN 1 AND 7))
);

CREATE INDEX habits_user_id_idx ON habits (user_id);

-- A day a habit was done. The row is the tick: there is no "not done" row,
-- and a day is ticked at most once.
CREATE TABLE habit_checks (
    habit_id UUID NOT NULL REFERENCES habits (id) ON DELETE CASCADE,
    day      DATE NOT NULL,
    PRIMARY KEY (habit_id, day)
);
