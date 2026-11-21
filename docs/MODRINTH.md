# Modrinth and CurseForge publishing

A tag push sends DimensionStructureVariants to GitHub, Modrinth and CurseForge. `.github/workflows/release.yml` holds one job, and that job calls `reusable-multiloader-release.yml` from `brainage04/MinecraftModConventions` at `v3.5.0`.

## The tag

Nothing but a tag push starts the workflow. The tag is `v` plus `mod_version` from `gradle.properties`. With `mod_version=1.0.0` you push `v1.0.0`. A tag that does not match stops the run in the preparation job, before anything is built or uploaded.

## What gets built

Preparation runs `./gradlew clean build` and then looks in `build/libs` for two files. The Fabric release is `dimensionstructurevariants-1.0.0.jar`, built from `archives_base_name` and `mod_version`. The NeoForge release is the single file matching `dimensionstructurevariants-neoforge-*.jar`. If that pattern finds nothing, or finds two files, the release fails.

Both files go onto the GitHub release. Its title is `DimensionStructureVariants v1.0.0`, because this repository passes `release_name` as `DimensionStructureVariants` followed by the tag. Its body is the annotated tag message, or GitHub's generated notes when the tag has no message. A hyphen in the tag, `v1.0.0-rc1` for example, marks the release as a prerelease.

## Where it goes

Five jobs follow preparation, one per destination:

| Job | Publishes |
| --- | --- |
| `github` | the GitHub release with both JARs |
| `modrinth-fabric` | the Fabric JAR to Modrinth |
| `modrinth-neoforge` | the NeoForge JAR to the same Modrinth project |
| `curseforge-fabric` | the Fabric JAR to CurseForge |
| `curseforge-neoforge` | the NeoForge JAR to CurseForge |

Both Modrinth jobs write to one project. The Fabric job finds or creates it. The NeoForge job waits for that job and takes the project id from it. Modrinth ends up with two versions per release, `1.0.0` on the `fabric` loader and `1.0.0-neoforge` on the `neoforge` loader, and the different version numbers keep them apart. Each lists Minecraft `26.3`, which comes from `minecraft_version`.

CurseForge gets the slug `dimension-structure-variants` and the numeric id in the `CURSEFORGE_PROJECT_ID` variable.

## Secrets

Set these under the repository's Settings, Secrets and variables, Actions, on the Secrets tab. `release.yml` reads them as `secrets.*`.

- `MODRINTH_TOKEN`, a Modrinth personal access token.
- `CURSEFORGE_TOKEN`, a CurseForge API token.

The GitHub release needs no secret of its own, because GitHub supplies `GITHUB_TOKEN` and the workflow passes it along.

The Modrinth token needs five scopes, because the workflow creates the project, reads it, updates it, lists its versions and uploads a version:

- `PROJECT_CREATE`
- `PROJECT_READ`
- `PROJECT_WRITE`
- `VERSION_READ`
- `VERSION_CREATE`

## Variables

Set these on the Variables tab. `release.yml` reads them as `vars.*`, so a value stored as a secret arrives empty.

- `MODRINTH_PROJECT_ID`, optional and can stay empty. The Fabric job resolves the project from the slug rather than from this variable, creating it as a draft project when nothing is there, and the NeoForge job reuses the id it found. The slug is the `fabric.mod.json` id, `dimensionstructurevariants`, unless `.modrinth/project.json` names another.
- `CURSEFORGE_PROJECT_ID`, required for CurseForge. It holds the numeric project id.

## When a step is skipped

Without `MODRINTH_TOKEN` both Modrinth jobs log a notice and stop. The GitHub release still happens. Without `CURSEFORGE_TOKEN` the two CurseForge jobs do the same. A `CURSEFORGE_TOKEN` with an empty `CURSEFORGE_PROJECT_ID` is different: that job fails.

A Modrinth version number that already exists on the project is not uploaded again, because the job lists the project's versions first. A GitHub release that already exists for the tag is edited, and its assets are replaced, rather than created twice.

Project and icon synchronization is allowed to fail without stopping the job, but the version upload then fails too. The project id it needs is exported only when synchronization succeeded, so a failed sync leaves the stale project metadata in place and no new version on the project.

## Repository visibility

The pinned workflow never asks GitHub whether the repository is public. Visibility plays no part. The tokens, the project ids and the `publish_modrinth` and `publish_curseforge` inputs decide whether a store job runs, and the two NeoForge jobs also need `additional_artifact_pattern` set.

## What the Modrinth project says

The Fabric job refreshes part of the project on every release, and sets the rest only when it creates the project.

Refreshed on every release: the summary, the body, the issues, source, wiki and licence links, and the side support. The body is `README.md`. The summary is the GitHub repository description, then `.modrinth/project.json`, then the `fabric.mod.json` description. A missing source link falls back to the GitHub repository URL, a missing issues or wiki link to that URL plus `/issues` or `/wiki`, and the licence link to `<repository>/blob/HEAD/LICENSE`.

Set only when the project is created: the slug, the title, the licence id, the categories, the additional categories, the Discord link, the donation links and the project type. `.modrinth/project.json` sets the primary category `worldgen` and no additional category. A rename, a licence change or a new category therefore needs an edit on Modrinth itself, because the next release does not send it.

The icon is `common/src/main/resources/assets/dimensionstructurevariants/icon.png`. It is replaced only when the current Modrinth icon URL does not already contain the SHA-1 of that file.

## Version dependencies

Version dependencies come from the `depends`, `recommends`, `suggests`, `conflicts` and `breaks` blocks of `fabric.mod.json`, with each mod id read as a Modrinth slug. `minecraft`, `java` and `fabricloader` are dropped, which leaves `fabric-api` as the only one listed. To change one, add a `dependency_overrides` entry to `.modrinth/project.json` with `project_id`, `project_slug` or `dependency_type`, or drop it with `skip`. A `version.dependencies` array in the same file adds entries that do not come from `fabric.mod.json`.

`fabric.mod.json` sets `environment` to `*` and declares no client entrypoint, so the project is marked server-side only. The NeoForge version lists no dependencies at all, because this repository has no `.modrinth/neoforge-dependencies.json` for the NeoForge job to read.

## Running the tasks locally

`./gradlew :fabric:publishModrinth -PpublishModrinth=true -PmodPublishingDryRun=true` runs the same tasks without writing to Modrinth. The dry run logs what each step would do and writes no remote state.

To cut the tag, follow `docs/RELEASE.md`.
