I'm picking up work on the dropbox2d project (a libGDX/Box2D arcade game) from a prior chat
session run by a different user/account. Before doing anything else:

1. Read `HANDOFF.md` in the repo root (`C:\Users\foukas\Documents\Work\Programming\AI\Claude\Test\HANDOFF.md`)
   in full — it has environment facts, standing decisions, and workflow conventions that
   aren't derivable from the code alone.
2. Read `TODOS.md` in the repo root for the current backlog.
3. Read the seesaw design doc at
   `C:\Users\foukas\.gstack\projects\Test\foukas-main-design-20260820-150834.md` — note the
   gstack slug mismatch called out in HANDOFF.md (docs live under `Test/`, not
   `foukas-dropbox2d/`). This doc is eng-reviewed and CLEARED (see its
   `## GSTACK REVIEW REPORT` section) with a corrected 10-step implementation plan in its
   Next Steps section. Implementation has not started yet.
4. Run `git log --oneline -20` to see what's actually landed.

Once you've read all four, summarize back to me in a few sentences: what's shipped, what the
seesaw feature is and why it's the first Box2D joint in this codebase, and what its next
step would be. Do NOT assume any standing autonomous commit/push permission from the prior
session — ask me explicitly if you think you need it. Then ask me whether I want to start
implementing the seesaw's Next Steps now, revisit anything in TODOS.md, or something else.
