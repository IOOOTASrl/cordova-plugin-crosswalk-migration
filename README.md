Cordova Crosswalk Data Migration Plugin forked from:
https://github.com/salesfusion-dev/cordova-plugin-crosswalk-migration


Added a popup message dialog during migration and application 
autorestart after successful migration.

You can customize popup dialog title and message by using these global
preferences:

```xml
<preference name="XwalkMigration.alertTitle" value="TITLE" />
<preference name="XwalkMigration.alertMessage" value="MESSAGE">
```