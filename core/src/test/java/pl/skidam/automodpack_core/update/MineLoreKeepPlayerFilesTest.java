package pl.skidam.automodpack_core.update;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

import org.junit.jupiter.api.Test;

import pl.skidam.automodpack_core.config.ClientConfigJsons;
import pl.skidam.automodpack_core.config.GenerationJsons;
import pl.skidam.automodpack_core.config.ModpackJsons;
import pl.skidam.automodpack_core.loader.PinnedMods;
import pl.skidam.automodpack_core.modpack.generation.OwnershipLedger;
import pl.skidam.automodpack_core.update.UpdatePlan.*;

/** MineLore: AutoModpack never deletes, archives or overwrites a file it did not put in the game directory itself. */
class MineLoreKeepPlayerFilesTest {
	private static final String PACK_HASH = "1111111111111111111111111111111111111111";
	private static final String PLAYER_HASH = "2222222222222222222222222222222222222222";
	private static final String OTHER_HASH = "3333333333333333333333333333333333333333";

	/** The Fabulously Optimized wipe: the server once shipped these exact bytes, and the player has them too. */
	@Test
	void firstInstallKeepsPlayerFilesThatMatchServerTombstones() {
		ModpackJsons.ModpackContentFields target = manifest(Map.of("mods/bbs.jar", item("mods/bbs.jar", PACK_HASH, 9, "mod")),
				ledger(entry("mods/bbs.jar", PACK_HASH, 9, OwnershipLedger.Status.PRESENT),
						entry("mods/sodium-extra.jar", PLAYER_HASH, 8, OwnershipLedger.Status.TOMBSTONE),
						entry("config/sodium-extra-options.json", PLAYER_HASH, 8, OwnershipLedger.Status.TOMBSTONE)));
		Map<FileKey, FileState> files = Map.of(
				new FileKey(Root.GAME_DIR, "mods/sodium-extra.jar"), new FileState(PLAYER_HASH, 8, true),
				new FileKey(Root.GAME_DIR, "config/sodium-extra-options.json"), new FileState(PLAYER_HASH, 8, true));

		UpdatePlan plan = UpdatePlanner.plan(new UpdatePlanner.Input(null, target, files, Set.of(), List.of(),
				List.of(new ModInfo("mods/sodium-extra.jar", PLAYER_HASH, 8, "1.0.0", Set.of("sodium-extra"), Set.of())), List.of(), List.of(), null,
				new ClientConfigJsons.ClientConfigFieldsV3()));

		assertNoGameDirChange(plan, "mods/sodium-extra.jar");
		assertNoGameDirChange(plan, "config/sodium-extra-options.json");
		assertTrue(plan.operations().stream().anyMatch(op -> op.root() == Root.PROJECTION && op.relativePath().equals("mods/bbs.jar")), "the pack still installs");
	}

	@Test
	void firstInstallConsentRemovesNothing() {
		String path = "mods/local.jar";
		FileState local = new FileState(PLAYER_HASH, 8, true);
		UpdatePlanner.Input input = new UpdatePlanner.Input(null, manifest(Map.of(), ledger()), Map.of(new FileKey(Root.GAME_DIR, path), local), Set.of(), List.of(),
				List.of(new ModInfo(path, PLAYER_HASH, 8, "1.0.0", Set.of("controlify"), Set.of())), List.of(), List.of(), null,
				new ClientConfigJsons.ClientConfigFieldsV3(), Map.of(path, local));

		assertNoGameDirChange(UpdatePlanner.plan(input), path);
	}

	@Test
	void sameIdDuplicateKeepsThePlayersJar() {
		ModpackJsons.ModpackContentFields target = manifest(Map.of("mods/server.jar", item("mods/server.jar", PACK_HASH, 9, "mod")),
				ledger(entry("mods/server.jar", PACK_HASH, 9, OwnershipLedger.Status.PRESENT)));
		Map<FileKey, FileState> files = Map.of(new FileKey(Root.PROJECTION, "mods/server.jar"), new FileState(PACK_HASH, 9, true),
				new FileKey(Root.GAME_DIR, "mods/local.jar"), new FileState(PLAYER_HASH, 8, true));

		UpdatePlan plan = UpdatePlanner.plan(new UpdatePlanner.Input(null, target, files, Set.of(),
				List.of(new ModInfo("mods/server.jar", PACK_HASH, 9, "1.0.0", Set.of("sodium"), Set.of())),
				List.of(new ModInfo("mods/local.jar", PLAYER_HASH, 8, "1.0.0", Set.of("sodium"), Set.of())), List.of(), List.of(), null,
				new ClientConfigJsons.ClientConfigFieldsV3()));

		assertNoGameDirChange(plan, "mods/local.jar");
		assertTrue(plan.conflicts().isEmpty());
	}

	@Test
	void byteIdenticalPlayerCopyIsKept() {
		ModpackJsons.ModpackContentFields target = manifest(Map.of("mods/server.jar", item("mods/server.jar", PACK_HASH, 9, "mod")),
				ledger(entry("mods/server.jar", PACK_HASH, 9, OwnershipLedger.Status.PRESENT)));
		Map<FileKey, FileState> files = Map.of(new FileKey(Root.PROJECTION, "mods/server.jar"), new FileState(PACK_HASH, 9, true),
				new FileKey(Root.GAME_DIR, "mods/server.jar"), new FileState(PACK_HASH, 9, true));

		UpdatePlan plan = UpdatePlanner.plan(new UpdatePlanner.Input(null, target, files, Set.of(),
				List.of(new ModInfo("mods/server.jar", PACK_HASH, 9, "1.0.0", Set.of("sodium"), Set.of())),
				List.of(new ModInfo("mods/server.jar", PACK_HASH, 9, "1.0.0", Set.of("sodium"), Set.of())), List.of(), List.of(), null,
				new ClientConfigJsons.ClientConfigFieldsV3()));

		assertNoGameDirChange(plan, "mods/server.jar");
	}

	/** An installed pack dropping a mod still cleans its own projection, never the player's mods folder. */
	@Test
	void installedPackDroppingAModLeavesThePlayersSameBytesAlone() {
		ModpackJsons.ModpackContentFields installed = manifest(Map.of("mods/old.jar", item("mods/old.jar", PLAYER_HASH, 8, "mod")),
				ledger(entry("mods/old.jar", PLAYER_HASH, 8, OwnershipLedger.Status.PRESENT)));
		ModpackJsons.ModpackContentFields target = manifest(Map.of(), ledger(entry("mods/old.jar", PLAYER_HASH, 8, OwnershipLedger.Status.TOMBSTONE)));
		Map<FileKey, FileState> files = Map.of(new FileKey(Root.PROJECTION, "mods/old.jar"), new FileState(PLAYER_HASH, 8, true),
				new FileKey(Root.GAME_DIR, "mods/old.jar"), new FileState(PLAYER_HASH, 8, true));

		UpdatePlan plan = UpdatePlanner.plan(new UpdatePlanner.Input(installed, target, files, Set.of(), List.of(),
				List.of(new ModInfo("mods/old.jar", PLAYER_HASH, 8, "1.0.0", Set.of("old"), Set.of())), List.of(), List.of(), null,
				new ClientConfigJsons.ClientConfigFieldsV3()));

		assertNoGameDirChange(plan, "mods/old.jar");
		assertTrue(plan.operations().stream().anyMatch(op -> op.root() == Root.PROJECTION && op.relativePath().equals("mods/old.jar") && op.operation() == OperationType.DELETE));
	}

	/** AutoModpack still owns and replaces its own nested copies. */
	@Test
	void ownNestedCopiesAreStillManaged() {
		NestedCopy previous = new NestedCopy("mods/nested.jar", PACK_HASH, 9);
		Map<FileKey, FileState> files = Map.of(new FileKey(Root.GAME_DIR, "mods/nested.jar"), new FileState(PACK_HASH, 9, true));

		UpdatePlan plan = UpdatePlanner.plan(new UpdatePlanner.Input(null, manifest(Map.of(), ledger()), files, Set.of(), List.of(), List.of(), List.of(previous), List.of(), null,
				new ClientConfigJsons.ClientConfigFieldsV3()));

		assertTrue(plan.operations().stream().anyMatch(op -> op.root() == Root.GAME_DIR && op.relativePath().equals("mods/nested.jar") && op.operation() == OperationType.DELETE));
	}

	@Test
	void everyPlayerJarIsPinnedButAutoModpacksOwnAreNot() {
		Set<String> ids = PinnedMods.protectedIds(List.of(), List.of(Set.of("sodium"), Set.of("automodpack_generated"), Set.of("automodpack_mod")));

		assertEquals(Set.of("sodium"), ids);
	}

	private static void assertNoGameDirChange(UpdatePlan plan, String path) {
		assertTrue(plan.operations().stream().noneMatch(op -> op.root() == Root.GAME_DIR && op.relativePath().equals(path)),
				() -> "player file " + path + " was touched: " + plan.operations());
	}

	private static ModpackJsons.ModpackContentFields manifest(Map<String, ModpackJsons.ModpackContentFields.ModpackContentItem> items,
			GenerationJsons.OwnershipLedgerFields ownershipLedger) {
		ModpackJsons.ModpackContentFields target = new ModpackJsons.ModpackContentFields(new LinkedHashSet<>(items.values()));
		target.modpackId = "abc1234";
		target.contentToken = "1".repeat(40);
		target.policySha1 = "2".repeat(40);
		target.ownershipLedger = ownershipLedger;
		return target;
	}

	private static ModpackJsons.ModpackContentFields.ModpackContentItem item(String path, String hash, long size, String type) {
		return new ModpackJsons.ModpackContentFields.ModpackContentItem(path, size, type, false, hash, "0");
	}

	private static GenerationJsons.OwnershipLedgerFields ledger(OwnershipLedger.Entry... entries) {
		Map<String, OwnershipLedger.Entry> values = new TreeMap<>();
		for (OwnershipLedger.Entry entry : entries) values.put(entry.logicalPath(), entry);
		return new OwnershipLedger("abc1234", values).toFields();
	}

	private static OwnershipLedger.Entry entry(String path, String hash, long size, OwnershipLedger.Status status) {
		NavigableSet<OwnershipLedger.Content> hashes = new TreeSet<>(Comparator.comparing(OwnershipLedger.Content::sha1).thenComparingLong(OwnershipLedger.Content::size));
		hashes.add(new OwnershipLedger.Content(hash, size));
		return new OwnershipLedger.Entry(path, hashes, new TreeSet<>(Set.of("main")), status);
	}
}
