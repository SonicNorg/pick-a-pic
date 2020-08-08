[![Build Status](https://travis-ci.com/SonicNorg/pick-a-pic.svg?branch=master)](https://travis-ci.com/SonicNorg/pick-a-pic)
# Pick-a-pic Telegram bot
#### Предназначен для организации голосований путем циклического выбора понравившейся картинки, каждый раз из двух вариантов.
Расчет рейтинга производится по системе Эло.
#### Для запуска последней версии:
`docker run -d --name pickapic -v /opt/pick-a-pic:/app/data -e config=/app/data/config.yaml --restart always norg/pick-a-pic:master`
