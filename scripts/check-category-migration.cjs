// Run: node scripts/check-category-migration.cjs
// Executes the production migration SQL against synthetic records, never user data.
const { DatabaseSync } = require('node:sqlite');
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const root = path.resolve(__dirname, '..');
const read = p => fs.readFileSync(path.join(root, p), 'utf8');
const base = 'app/src/main/java/com/smartclipboard/app/data/';
const source = read(base + 'ClipboardDatabase.kt');
const schema = version => JSON.parse(read(`app/schemas/com.smartclipboard.app.data.ClipboardDatabase/${version}.json`)).database;
function create(db, version) {
  for (const entity of schema(version).entities) {
    db.exec(entity.createSql.replaceAll('${TABLE_NAME}', entity.tableName));
    for (const index of entity.indices) db.exec(index.createSql.replaceAll('${TABLE_NAME}', entity.tableName));
  }
}
const sqlCalls = text => [...text.matchAll(/db\.execSQL\("([^"]+)"/g)].map(m => m[1]);
const db = new DatabaseSync(':memory:');
create(db, 2);
const insert = db.prepare('INSERT INTO ClipboardItem(content, createdTime, updatedTime, category, tags, favorite, useCount, lastUsedTime) VALUES(?, 1, 2, ?, ?, 1, 7, 99)');
for (const category of ['文本', '网址', '邮箱', '历史自定义']) insert.run(`migration fixture ${category}`, category, 'fixture');
const before = db.prepare('SELECT * FROM ClipboardItem ORDER BY id').all();
const migration = source.split('val migration2To3')[1].split('private val migration1To2')[0];
for (const sql of sqlCalls(migration)) db.exec(sql);
const seed = source.split('private fun seedCategories')[1].split('val migration2To3')[0];
const uncategorized = read(base + 'LibraryCategory.kt').match(/UNCATEGORIZED = "([^"]+)"/)[1];
const names = seed.match(/listOf\(([^)]+)\)/)[1].split(',').map(s => s.trim() === 'UNCATEGORIZED' ? uncategorized : JSON.parse(s.trim()));
const [seedOne, seedLegacy] = sqlCalls(seed);
for (const name of names) db.prepare(seedOne).run(name);
db.exec(seedLegacy);
assert.deepEqual(db.prepare('SELECT * FROM ClipboardItem ORDER BY id').all(), before);
assert.equal(db.prepare('SELECT name FROM LibraryCategory WHERE id=1').get().name, uncategorized);
for (const item of before) assert.ok(db.prepare('SELECT id FROM LibraryCategory WHERE name=?').get(item.category));

// Compare actual migrated shape with Room's generated v3 schema.
const fresh = new DatabaseSync(':memory:');
create(fresh, 3);
for (const entity of schema(3).entities) {
  assert.deepEqual(db.prepare(`PRAGMA table_info(${entity.tableName})`).all(), fresh.prepare(`PRAGMA table_info(${entity.tableName})`).all());
  assert.deepEqual(db.prepare(`PRAGMA index_list(${entity.tableName})`).all(), fresh.prepare(`PRAGMA index_list(${entity.tableName})`).all());
}
const dao = read(base + 'ClipboardItemDao.kt');
const queryFor = fn => dao.match(new RegExp('@Query\\("([^"\\n]+)"\\)\\s+suspend fun ' + fn))[1];
const rename = db.prepare(queryFor('renameCategory'));
db.exec('BEGIN');
db.prepare('UPDATE LibraryCategory SET name=? WHERE name=?').run('重命名', '历史自定义');
rename.run({ oldName: '历史自定义', newName: '重命名' });
db.exec('COMMIT');
assert.equal(db.prepare("SELECT category FROM ClipboardItem WHERE id=4").get().category, '重命名');
db.exec('BEGIN');
rename.run({ oldName: '重命名', newName: uncategorized });
db.prepare('DELETE FROM LibraryCategory WHERE name=?').run('重命名');
db.exec('COMMIT');
assert.equal(db.prepare('SELECT count(*) AS n FROM ClipboardItem').get().n, before.length);
assert.equal(db.prepare('SELECT category FROM ClipboardItem WHERE id=4').get().category, uncategorized);
assert.throws(() => db.prepare('INSERT INTO LibraryCategory(name, iconFile) VALUES(?, NULL)').run(uncategorized));
assert.equal(db.prepare('SELECT count(*) AS n FROM ClipboardItem i LEFT JOIN LibraryCategory c ON i.category=c.name WHERE c.id IS NULL').get().n, 0);
// Batch deletion uses real DAO SQL with synthetic IDs; unrelated rows must survive.
for (let i = 0; i < 1005; i++) insert.run(`bulk fixture ${i}`, uncategorized, 'fixture');
const allRows = db.prepare('SELECT * FROM ClipboardItem ORDER BY id').all();
const selected = allRows.filter((_, index) => index % 5 !== 0).map(row => row.id);
const selectedSet = new Set(selected);
const deleteChunk = ids => db.prepare(queryFor('deleteSelected').replace(':ids', ids.map(() => '?').join(','))).run(...ids);
db.exec('BEGIN');
deleteChunk(selected.slice(0, 400));
db.exec('ROLLBACK');
assert.deepEqual(db.prepare('SELECT * FROM ClipboardItem ORDER BY id').all(), allRows);
db.exec('BEGIN');
for (let i = 0; i < selected.length; i += 400) deleteChunk(selected.slice(i, i + 400));
db.exec('COMMIT');
assert.deepEqual(db.prepare('SELECT * FROM ClipboardItem ORDER BY id').all(), allRows.filter(row => !selectedSet.has(row.id)));
db.close(); fresh.close();
console.log('PASS: multi-chunk selected deletion, rollback, preservation of unselected records.');
console.log('PASS: v2 -> v3 data preservation, generated schema parity, legacy categories, rename, delete/reassign, uniqueness.');
