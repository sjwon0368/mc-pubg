# Battlegrounds PUBG-style minigame for Denizen

# Usage: /ex run startbattle <farm_minutes>
# All players in the world are included.

battlegrounds_vars:
    type: world
    debug: false
    script:
    - define game_active false
    - define alive_players 0
    - define winner none
    - define frozen_players li@
    - define farm_bar none
    - define storm_bar none

startbattle:
    type: command
    name: startbattle
    description: Starts the battlegrounds game.
    usage: /startbattle [farm_minutes]
    permission: battlegrounds.start
    script:
    - if <server.flag[game_active].as_boolean||false> {
        - narrate "<&c>게임이 이미 진행 중입니다."
        - queue clear
      }
    - define farm_minutes <context.args.get[1]||10>
    - if <def[farm_minutes].is[!=].number> || <def[farm_minutes].as_int.is[or_less].than[1]> {
        - narrate "<&c>파밍 시간은 1분 이상 숫자로 입력하세요."
        - queue clear
      }
    - flag server game_active:true
    - flag server alive_players:<server.list_online_players.size>
    - flag server winner:!
    - flag server frozen_players:li@
    - flag server farm_bar:!
    - flag server storm_bar:!
    - execute as_server "gamerule keepInventory true"
    - execute as_server "worldborder center 0 0"
    - execute as_server "worldborder set 2000"
    # Clear inventories and teleport all players
    - foreach <server.list_online_players>:
        - inventory clear <def[value]>
        - teleport <def[value]> 0,100,0,0,0
        - execute as_op "gamemode survival <def[value].name>"
        - title <def[value]> title:<&f><&l>파밍 시간이 시작되었습니다: <def[farm_minutes]>분 subtitle: duration:10,60,10
    # Setup bossbar for farming
    - bossbar create battlegrounds_farm_bar "남은 파밍 시간: <def[farm_minutes]>분" color:yellow style:solid
    - bossbar show battlegrounds_farm_bar <server.list_online_players>
    - flag server farm_bar:battlegrounds_farm_bar
    # Start farming timer
    - run battlegrounds_farm_timer farm_minutes:<def[farm_minutes]> instantly

battlegrounds_farm_timer:
    type: task
    definitions: farm_minutes
    script:
    - define farm_seconds <def[farm_minutes].as_int.mul[60]>
    - define end_time <util.date_time_millis.add[<def[farm_seconds].mul[1000]>]>
    - while <util.date_time_millis.is[or_less].than[<def[end_time]>]>:
        - define left <def[end_time].sub[<util.date_time_millis>].div[1000]>
        - define min <def[left].div[60].as_int>
        - define sec <def[left].mod[60].as_int>
        - if <def[min].is[or_more].than[1]> {
            - bossbar update battlegrounds_farm_bar "남은 파밍 시간: <def[min]>분 <def[sec]>초"
          }
          else {
            - bossbar update battlegrounds_farm_bar "남은 파밍 시간: <def[left].round_to[1]>초"
          }
        - bossbar progress battlegrounds_farm_bar <def[left].div[<def[farm_seconds]>]>
        - wait 1s
    # Farming ended
    - bossbar update battlegrounds_farm_bar "파밍 시간이 종료되었습니다!"
    - bossbar progress battlegrounds_farm_bar 0
    - foreach <server.list_online_players>:
        - title <def[value]> title:<&f><&l>파밍 시간이 종료되었습니다! subtitle: duration:10,100,10
    - wait 5s
    - bossbar remove battlegrounds_farm_bar
    - flag server farm_bar:!
    - run battlegrounds_game_countdown instantly

battlegrounds_game_countdown:
    type: task
    script:
    - foreach <server.list_online_players>:
        # Teleport to random position
        - define x <util.random.int[-1000,1000]>
        - define z <util.random.int[-1000,1000]>
        - teleport <def[value]> <def[x]>,100,<def[z]>,0,0
        - potion effect add <def[value]> SLOW_FALLING duration:600 amplifier:1
        - flag server frozen_players:<-:<def[value].uuid>
        - execute as_op "gamemode survival <def[value].name>"
        - title <def[value]> title:<&f><&l>게임이 잠시 후 시작됩니다 subtitle:<&f><&l>시작까지 15초 duration:10,60,10
        - adjust <def[value]> walk_speed:0
        - adjust <def[value]> fly_speed:0
    - define count 15
    - while <def[count].is[or_more].than[1]>:
        - foreach <server.list_online_players>:
            - if <def[count].is[<=].to[3]> {
                - title <def[value]> title:<&c><&l><def[count]> duration:0,20,0
                - playsound <def[value]> BLOCK_NOTE_BLOCK_BIT volume:1 pitch:1
              }
              else if <def[count].is[==].to[2]> {
                - title <def[value]> title:<&6><&l>2 duration:0,20,0
                - playsound <def[value]> BLOCK_NOTE_BLOCK_BIT volume:1 pitch:1
              }
              else if <def[count].is[==].to[1]> {
                - title <def[value]> title:<&a><&l>1 duration:0,20,0
                - playsound <def[value]> BLOCK_NOTE_BLOCK_BIT volume:1 pitch:1
              }
        - define count <def[count].sub[1]>
        - wait 1s
    # Unfreeze and start
    - foreach <server.list_online_players>:
        - flag server frozen_players:<-:<def[value].uuid>
        - adjust <def[value]> walk_speed:0.2
        - adjust <def[value]> fly_speed:0.1
        - title <def[value]> title:<&a><&l>- 시작! - duration:0,40,10
        - playsound <def[value]> BLOCK_NOTE_BLOCK_BIT volume:1 pitch:2
    - run battlegrounds_start_storm instantly

battlegrounds_start_storm:
    type: task
    script:
    - flag server alive_players:<server.list_online_players.size>
    - execute as_server "gamerule keepInventory false"
    # Setup storm bossbar
    - bossbar create battlegrounds_storm_bar "자기장 축소 중 (1/6)" color:red style:solid
    - bossbar show battlegrounds_storm_bar <server.list_online_players>
    - flag server storm_bar:battlegrounds_storm_bar
    - run battlegrounds_storm_shrink stage:0 instantly

battlegrounds_storm_shrink:
    type: task
    definitions: stage
    script:
    - define border_sizes li@2000|1000|500|200|50|20|5
    - define shrink_time 70
    - define pause_time 70
    - define shrink_stages <def[border_sizes].size.sub[1]>
    - if <def[stage].is[or_more].than[<def[shrink_stages]>]> {
        - execute as_server "worldborder set <def[border_sizes].get[<def[shrink_stages].add[1]>]>"
        - bossbar update battlegrounds_storm_bar "자기장 축소 완료"
        - bossbar progress battlegrounds_storm_bar 0
        - wait 5s
        - bossbar remove battlegrounds_storm_bar
        - flag server storm_bar:!
        - queue clear
      }
    - define from <def[border_sizes].get[<def[stage].add[1]>]>
    - define to <def[border_sizes].get[<def[stage].add[2]>]>
    - bossbar update battlegrounds_storm_bar "자기장 축소 중 (<def[stage].add[1]>/<def[shrink_stages]>)"
    - bossbar color battlegrounds_storm_bar red
    - execute as_server "worldborder set <def[to]> <def[shrink_time]>"
    - define shrink_end <util.date_time_millis.add[<def[shrink_time].mul[1000]>]>
    - while <util.date_time_millis.is[or_less].than[<def[shrink_end]>]>:
        - define left <def[shrink_end].sub[<util.date_time_millis>].div[1000]>
        - bossbar progress battlegrounds_storm_bar <def[left].div[<def[shrink_time]>]>
        - wait 1s
    # Pause after shrink
    - bossbar update battlegrounds_storm_bar "자기장 멈춤 (<def[to]>x<def[to]>)"
    - bossbar color battlegrounds_storm_bar yellow
    - define pause_end <util.date_time_millis.add[<def[pause_time].mul[1000]>]>
    - while <util.date_time_millis.is[or_less].than[<def[pause_end]>]>:
        - define left <def[pause_end].sub[<util.date_time_millis>].div[1000]>
        - bossbar progress battlegrounds_storm_bar <def[left].div[<def[pause_time]>]>
        - wait 1s
    - run battlegrounds_storm_shrink stage:<def[stage].add[1]> instantly

# Command blocking during game
on player command:
    - if <server.flag[game_active].as_boolean||false> && !<context.command.name.is[==].to[startbattle]> {
        - determine passively cancelled
        - narrate "<&c>게임 중에는 명령어를 사용할 수 없습니다." targets:<player>
        - announce "<&c><player.name>이(가) 명령어 사용을 시도하였습니다."
      }

# Gamemode change blocking during game
on player changes gamemode:
    - if <server.flag[game_active].as_boolean||false> && !<context.new_gamemode.is[==].to[spectator]> {
        - determine cancelled
        - narrate "<&c>게임 중에는 게임모드를 변경할 수 없습니다." targets:<player>
        - announce "<&c><player.name>이(가) 게임모드 변경을 시도하였습니다."
      }

# Player death handling
on player dies:
    - if !<server.flag[game_active].as_boolean||false> {
        - determine keep_inventory:true keep_level:true
        - queue clear
      }
    - determine keep_inventory:false keep_level:false
    - flag server alive_players:<server.flag[alive_players].sub[1]>
    - execute as_op "gamemode spectator <player.name>"
    - if <context.killer.exists> {
        - announce "<&e>[+] <context.killer.name>이(가) <player.name>을(를) 죽였습니다. 남은 생존자 수: <server.flag[alive_players]>"
      }
      else {
        - announce "<&e>[+] <player.name>이(가) 사망했습니다. 남은 생존자 수: <server.flag[alive_players]>"
      }
    - run battlegrounds_update_alive_hotbar instantly
    - if <server.flag[alive_players].is[==].to[1]> {
        - run battlegrounds_declare_winner instantly
      }

battlegrounds_update_alive_hotbar:
    type: task
    script:
    - foreach <server.list_online_players>:
        - if <def[value].gamemode.is[!=].to[spectator]> {
            - actionbar <def[value]> "<&e><&l>> 남은 생존자 수: <server.flag[alive_players]>명 <"
          }

battlegrounds_declare_winner:
    type: task
    script:
    - foreach <server.list_online_players>:
        - if <def[value].gamemode.is[!=].to[spectator]> {
            - flag server winner:<def[value].name>
            - adjust <def[value]> walk_speed:0
            - adjust <def[value]> fly_speed:0
          }
    - foreach <server.list_online_players>:
        - title <def[value]> title:<&6><&l>WINNER WINNER CHICKEN DINNER duration:0,60,10
        - playsound <def[value]> ENTITY_PLAYER_ATTACK_STRONG volume:1 pitch:1
    - wait 3s
    - foreach <server.list_online_players>:
        - if <server.flag[winner].exists> {
            - title <def[value]> title:<&e><&l><server.flag[winner]>! duration:0,60,10
            - playsound <def[value]> ENTITY_PLAYER_LEVELUP volume:1 pitch:1
          }
    - if <server.flag[winner].exists> {
        - announce "<&a><&l>< 게임 결과 >\n\n<&e><&l><server.flag[winner]> 승리!"
      }
    # End game: reset border, bossbars, actionbars, keepInventory
    - flag server game_active:false
    - bossbar remove battlegrounds_storm_bar
    - bossbar remove battlegrounds_farm_bar
    - execute as_server "worldborder set 2000"
    - foreach <server.list_online_players>:
        - actionbar <def[value]> ""
    - execute as_server "gamerule keepInventory true"
