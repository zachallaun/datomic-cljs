// reusable Node.js script for running clojurescript.test tests
// see http://github.com/cemerick/clojurescript.test for more info

var fs = require('fs');
var files = process.argv.slice(2);

for (var i = 0; i < files.length; i++) {
  var code = fs.readFileSync(files[i], 'utf8');
  // comment out leading shebang
  if (code[0] === '#')
    code = '//' + code;
  eval(code);
}

var results = cemerick.cljs.test.run_all_tests();
cljs.core.println(results);
var success = cemerick.cljs.test.successful_QMARK_(results)

process.exit(success ? 0 : 1);
