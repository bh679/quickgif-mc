<!-- gate: gate-1-plan | version: 1.1.0 -->
# Gate 1 — Plan Approval

**Trigger:** Before writing any code.

## Research & Reuse
**Mandatory before writing the plan.**

- **GitHub code search first:** Run `gh search repos` and `gh search code` to find existing implementations, templates, and patterns before writing anything new.
- **Check package registries:** Search npm, PyPI, crates.io, and other registries before writing utility code. Prefer battle-tested libraries over hand-rolled solutions.
- **Search for adaptable implementations:** Look for open-source projects that solve 80%+ of the problem and can be forked, ported, or wrapped.
- Prefer adopting or porting a proven approach over writing net-new code when it meets the requirement.

## Agent Actions
1. Research & reuse (see above)
2. Enter plan mode (`EnterPlanMode`)
3. Explore the codebase — read relevant files, understand existing patterns
4. Write a plan to the plan file covering:
   - What will be built
   - Which files will be changed and why
   - Estimated complexity
   - Risks or dependencies
   - Deployment impact (see checklist below)
5. Present the plan to the user via `ExitPlanMode`
6. After approval, ensure you are in a worktree on a `dev/` branch (see below) — **before writing any code**

**Gate requirement:** User clicks Approve in plan mode.

**Never skip:** Even for "simple" changes. Plan mode catches assumptions early.

## Post-Approval: Worktree & Branch

Immediately after plan approval, before writing any code, get into a worktree on a `dev/` branch.
Which step applies depends on how the session started — check first:

```bash
[ "$(git rev-parse --git-dir)" = "$(git rev-parse --git-common-dir)" ] \
  && echo "NOT in a worktree — do step 1" \
  || echo "already in a worktree — do step 2"
```

### Step 1 — Not in a worktree: create one

Every change needs a worktree (`git.md` § Git Worktrees — enforced by the pre-bash hook, which
blocks `git commit` outside one). Create it and do all work from there:

```bash
git worktree add .claude/worktrees/<feature-slug> -b dev/<feature-slug>
cd .claude/worktrees/<feature-slug>
```

### Step 2 — Already in a worktree: rename the branch

Worktree sessions auto-assign a `claude/<slug>` branch name. Rename it to `dev/<feature-slug>`:

```bash
# Derive a 3-5 word kebab-case slug from the feature name
OLD=$(git rev-parse --abbrev-ref HEAD)          # e.g. claude/competent-joliot-87b1ca
NEW="dev/<feature-slug>"                         # e.g. dev/ender-chest-persistence

git branch -m "$OLD" "$NEW"
git push origin "$NEW"
git push origin --delete "$OLD"
git branch --set-upstream-to="origin/$NEW" "$NEW"
```

This keeps all PRs, CI runs, and branch history under the standard `dev/` prefix.

## Related Playbooks
Before creating or searching board items, read `~/.claude/playbooks/project-board.md`.

## Deployment Impact Checklist
Assess whether the planned changes impact deployment. Flag if any apply:

- Environment variable additions, removals, or changes
- New dependencies or major version bumps
- Port or networking changes
- Database schema migrations
- New API endpoints requiring proxy/load balancer config
- Docker/container configuration changes
- Build step changes (new tools, changed commands, new artifacts)
- New external service integrations (credentials/config needed)
- Startup or shutdown procedure changes
- Infrastructure requirement changes (memory, CPU, storage)

**If any apply:**
1. Check for existing `Deployment-*.md` wiki pages
2. Include "Update deployment documentation" as a task in the plan
3. Note which deployment methods are affected

**If no deployment docs exist yet:** create them after Gate 3 (see gate-3-merge.md).

## Session Identification
Update the session title on entering this gate:

**Title format:** `PLAN - <Task Name> - <Project Name>`

| Status code | Meaning |
|---|---|
| `IDEA` | Exploring / not yet started |
| `PLAN` | Gate 1 in progress |
| `DEV` | Implementing |
| `TEST` | Gate 2 in progress |
| `DONE` | Merged and shipped |

Update the title on every status transition. Sync to GitHub Project V2 board item if applicable.
