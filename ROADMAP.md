# Roadmap

Functional feature roadmap for workout, built step by step in small, single-topic PRs. Each entry
is checked off in the PR that completes it.

Fixed technical decisions: Spring AI + Anthropic (Claude) for all AI steps; UI is bilingual de/en
via Transloco; multi-tenant from the start (`tenant_id` on every tenant-owned table, one shared
database) with two user groups per tenant, admin and user, and super-users above the tenants.

## Foundation

Taken over from the frontdesk app as the starting point:

- [x] **Shell** — sidebar with grouped navigation and the tenant's branding, top bar with theme
      settings (primary colour, surface, preset) and dark mode, a start page as placeholder.
- [x] **Sign-in** — session cookie (Spring Session JDBC), CSRF, login with the tenant's Kennung;
      super-users sign in without one.
- [x] **Profile** — own data, avatar, password change.
- [x] **User management** — admins create, edit, activate and deactivate the users of their
      tenant; the list is a working table with column toggle, reorder and resize.
- [x] **Company** — name, website, logo, brand colour and the branches (headquarters and
      Filialen) with address and contact data.
- [x] **Tenant management** — a super-user creates, renames, opens and deletes tenants on the
      Mandanten page; a super-user is created from the configuration at first start.
- [x] **AI access and costs** — tenants may bring their own Anthropic key, stored encrypted, with
      the platform key as fallback; every call to the model is recorded with its tokens and
      priced from a table in the configuration, and an admin page shows the sums.

## Habits

- [x] **Habit tracker** — the start page: every user keeps their own habits, daily or a number of
      days per calendar week, optionally grouped into categories, and ticks them off in a week grid
      with progress and streaks; past days can be back-filled, habits archived or deleted.
      Design: [`docs/specs/2026-10-09-habit-tracker-design.md`](docs/specs/2026-10-09-habit-tracker-design.md).
- [ ] **Dashboard** — a heatmap per habit over the last weeks and completion rates, built on the
      backend's week report.

## Operations and security

- [ ] **Forgotten password** and **invitation by mail** for new users; today an admin generates
      a password and hands it over by other means.
- [ ] **Language switch** — the English translation exists but cannot be reached from the UI,
      and the PrimeNG texts are hard-coded German.
