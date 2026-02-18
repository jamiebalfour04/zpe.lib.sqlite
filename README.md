<h1>zpe.lib.sqlite</h1>

<p>
  This is the official SQLite plugin for ZPE.
</p>

<p>
  The plugin provides support for creating, querying and managing SQLite databases directly from ZPE.
</p>

<h2>Installation</h2>

<p>
  Place <strong>zpe.lib.sqlite.jar</strong> in your ZPE native-plugins folder and restart ZPE.
</p>

<p>
  You can also download with the ZULE Package Manager by using:
</p>
<p>
  <code>zpe --zule install zpe.lib.sqlite.jar</code>
</p>

<h2>Documentation</h2>

<p>
  Full documentation, examples and API reference are available here:
</p>

<p>
  <a href="https://www.jamiebalfour.scot/projects/zpe/documentation/plugins/zpe.lib.sqlite/" target="_blank">
    View the complete documentation
  </a>
</p>

<h2>Example</h2>

<pre>
  
db = new SQLite()
db.open("test.db")

db.execute("CREATE TABLE IF NOT EXISTS users (id INTEGER PRIMARY KEY, name TEXT)")
db.execute("INSERT INTO users (name) VALUES (?)", ["Jamie"])

rows = db.query("SELECT * FROM users")

print(rows)

db.close()
</pre>

<h2>Notes</h2>

<ul>
  <li>Supports parameterised queries.</li>
  <li>Supports transactions (begin, commit, rollback).</li>
  <li>Requires permission level 3.</li>
</ul>
