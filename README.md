#PvP Protection for new players!#

##About##
The original author of this plugin (Psychobit) has seemingly left the minecraft community,
and this plugin had some features that I wanted to implement. As his license is
MIT, I 'forked' his code to enhance the campfire experience!

##Features##
* New players have 20 minutes (configurable) in which they are protected
* Protected players cannot take or give PvP damage
* Protected players cannot be set on fire by flint and steel
* Protected players cannot be lava bucketed
* Protected players cannot place lava, use flint and steel, open chests, or break chests ( prevents abuse )
* Protection automatically expires, and announces to the server when a player loses protection
* Players can terminate their protection early ( announces to server )
* Protection is reapplied upon dead (configurable)
* Protection timer stops when the player is offline
* Protection timer stops when in a WorldGuard region with no PvP or invincibility

##Commands##
`/campfire timeleft [Player]` - Tell the remaining protection time for a player ( defaults to self )
`/campfire terminate` - Turn off your protection early

Ops are completely immune to all protection conditions, as they cannot be protected, nor be protected against.

##Credits##
* Psychobit - the original version of this plugin!