#!/bin/bash
curl https://api.github.com/authorizations --user "YOUR_USER" --data '{"scopes":["admin:org"], "note":["YOUR_NOTE"], "client_id":"YOUR_ID", "client_secret":"YOUR_SECRET"}'
