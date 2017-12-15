#!/usr/bin/env bash
if [ ! -z "$2" ]
  then
    cd /home/zimbra/zcs/zm-mailbox/store
    ant compile publish-local deploy
fi
exec /home/zimbra/zcs/zm-core-utils/src/bin/testEBF $1