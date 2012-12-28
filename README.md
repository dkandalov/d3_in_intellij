What is this?
=============

Experimental IntelliJ plugins which pull data from IntelliJ and make it available as json
for visualization in a browser using [d3.js](https://github.com/mbostock/d3).<br/>
These plugins will only work within host [intellij-eval plugin](https://github.com/dkandalov/intellij_eval).

There are two plugins inside:
 - word cloud. Shows "tag cloud" of words/identifiers used in project.
 - treemap navigation. Size of treemap tiles is based on size of package/class (for Java source code only).

See screenshots below.


Why?
====
 - it's fun (and potentially useful) to see your project in a "different way"
 - it feels like there is a lot of data in IDEs and VCSs which we don't use properly. This is an attempt to make use of it.


How to use?
===========
This project is not "production-ready" and I don't know how it will evolve (if it will).<br/>
If you would like to try it, please read source code or send me a message/email.


Screenshots
===========
Treemap view of [IntelliJ community edition](https://github.com/JetBrains/intellij-community) at the project root.
Numbers below package names show estimated size of all classes it contains (estimated size ~= all statements + fields + method declarations).
<img src="https://github.com/dkandalov/d3_in_intellij/blob/master/screenshots/intellij-treemap.png?raw=true" alt="auto-revert screenshot" title="auto-revert screenshot" align="left" />

Treemap view for "com.intellij" package under "platform-impl" source root.
<img src="https://github.com/dkandalov/d3_in_intellij/blob/master/screenshots/intellij-treemap2.png?raw=true" alt="auto-revert screenshot" title="auto-revert screenshot" align="left" />

<br/><br/>
Word cloud based on plain text analysis (IntelliJ CE source code).
<img src="https://github.com/dkandalov/d3_in_intellij/blob/master/screenshots/intellij-wordcloud.png?raw=true" alt="auto-revert screenshot" title="auto-revert screenshot" align="left" />

<br/><br/>
Word cloud based on java identifiers (IntelliJ CE source code).
<img src="https://github.com/dkandalov/d3_in_intellij/blob/master/screenshots/intellij-identifier-cloud.png?raw=true" alt="auto-revert screenshot" title="auto-revert screenshot" align="left" />

