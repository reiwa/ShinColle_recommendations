Chinese version 
↓Japanese version below

以下是在 ShinColle MOD 1.12.2 移植过程中发现的错误和改进建议列表。


## 优先权 : ■■■■■

#### Issue : 如果放置了 “深海提督的办公桌 ”图块并将其销毁，然后在同一位置放置了不同的图块实体（如 ‘深海热泉’），则 “深海提督的办公桌 ”的纹理将叠加在一起。
#### Effect : 纹理显示错误、右键单击地砖时库存物品交换或消失、可能出现意外错误
#### Cause : 遗漏了瓦片实体删除流程的实施。

#### How to fix : 添加了瓦片实体删除程序。

shincolle/block/BlockDesk.java

```
+++ @Override
    public void onBlockHarvested(World worldIn, BlockPos pos, IBlockState state, EntityPlayer player) {
        super.onBlockHarvested(worldIn, pos, state, player);
        if (worldIn.isRemote) {
            worldIn.removeTileEntity(pos);
            worldIn.notifyBlockUpdate(pos, state, Blocks.AIR.getDefaultState(), 3);
            worldIn.markBlockRangeForRenderUpdate(pos, pos);
        }
    }
```


## 优先权 : ■■■■□

#### Issue : 在使用 Metamorph 模组变身为深海栖舰时，生命值会被固定为 4，或者在 4 和 25~35 之间被高速地反复覆盖
#### Effect : 体能显示错误，实际最大体能下降？
#### Cause : 超出 Metamorph MOD 规范，通过 .getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue() 对最大生命值进行覆盖的混战

#### How to fix : 使用 morph.settings 指定最大适合度

shincolle/intermod/MetamorphHelper.java

```
public static void onPlayerTickHelper(EntityPlayer player) {
...
	if (im != null && im.getCurrentMorph() instanceof EntityMorph) {
	...
		if (em != null && em.getEntity() instanceof IShipMorph) {

+++			AbstractMorph morph = Morphing.get(player).getCurrentMorph();
                EntityLivingBase target = ((EntityMorph) morph).getEntity(player.world);
                if(target instanceof BasicEntityShip){
                    morph.settings.health = (int)(20.0 + ((BasicEntityShip) target).getAttrs().getAttrsBuffed(0) * ConfigHandler.morphHPRatio);
                } else if(target instanceof BasicEntityShipHostile) {
                    morph.settings.health = 20;
                }

		...
```

Shincolle/entity/BasicEntityShip.java

```
public void calcShipAttributes(int flag, boolean sync) {
...
--- if (this.morphHost != null) {		this.morphHost.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(20D + this.shipAttrs.getAttrsBuffed(ID.Attrs.HP) * ConfigHandler.morphHPRatio);
    }
```

##### Note : 这个修复只解决了Morph的生命值显示问题，但可能会带来潜在的故障风险。


## 优先权 : ■■■□□

#### Issue : 点击装有 “ヲ级的指挥权杖 ”的深海舰船时，团队粒子的更新最多会延迟 32 个刻度。
#### Effect : 用户体验差。
#### Cause : 因为团队粒子更新固定为每 32 个刻度。

#### How to fix : 添加了点击深海飞船时的粒子更新过程。

shincolle/item/PointerItem.java

```
+++ private int coolDown = -1;

...

    @Override
    public boolean onEntitySwing(EntityLivingBase entity, ItemStack item) {
    ...
        RayTraceResult hitObj = EntityHelper.getPlayerMouseOverEntity(64D, 1F, exlist, true, false);
        if (hitObj != null) {
        ...
            if (hitObj.entityHit instanceof BasicEntityShip || hitObj.entityHit instanceof BasicEntityMount) {
            ...
                if (TeamHelper.checkSameOwner(player, ship) && capa != null) {
                ...
                    if (keySet.keyBindSneak.isKeyDown()) {
                    ...
                        if (i >= 0) {
                        ...

+++                            if((EntityHelper.getPointerInUse(player) != null && stack.getMetadata() < 3) || ConfigHandler.alwaysShowTeamParticle) {
                                coolDown = 2;
                            }

                            return true;
                        }
                    } else {
                    ...

+++                       if((EntityHelper.getPointerInUse(player) != null && item.getMetadata() < 3) || ConfigHandler.alwaysShowTeamParticle) {
                           coolDown = 2;
                       }

                       return true;
                   }

...

    @Override
    public void onUpdate(ItemStack stack, World world, Entity entity, int slot, boolean inUse) {
    ...
    if (player instanceof EntityPlayer && EntityHelper.getPointerInUse((EntityPlayer) player) != null && item.getMetadata() < 3 || ConfigHandler.alwaysShowTeamParticle) {
        if (world.isRemote) {

+++            if(coolDown > 0){
                coolDown --;
            }

⇄⇄⇄          if (entity.ticksExisted % 32 == 0 || coolDown == 0) {
            ...

```


## 优先权 : ■■■□□

#### Issue : 当持有带有右键事件的物品（如岩浆桶、末影珍珠等）并尝试打开深海棲舰的GUI时，右键事件会被触发。（比如倾倒岩浆或向深海棲舰投掷珍珠等）
#### Effect : 游戏玩家可能会进行非预期操作或出现意外故障。
#### Cause : 未实现右键单击事件的回避过程。

#### How to fix : 实现避免右键单击事件的处理。

shincolle/handler/EventHandler.java

```
+++@SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getItemStack().getItem() != Items.NAME_TAG && event.getItemStack().getItem() != Items.LEAD && getPointedEntity(event.getEntityPlayer().world, event.getEntityPlayer(), 5.0D) instanceof BasicEntityShip) {
            event.setResult(Event.Result.DENY);
            event.setCanceled(true);
            syncPlayerHand(event.getEntityPlayer(), event.getHand());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getItemStack().getItem() != Items.NAME_TAG && event.getItemStack().getItem() != Items.LEAD && getPointedEntity(event.getEntityPlayer().world, event.getEntityPlayer(), 5.0D) instanceof BasicEntityShip) {
            event.setUseBlock(Event.Result.DENY);
            event.setUseItem(Event.Result.DENY);
            event.setCanceled(true);
            syncPlayerHand(event.getEntityPlayer(), event.getHand());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onUseItemStart(LivingEntityUseItemEvent.Start event) {
        if (event.getEntityLiving() instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) event.getEntityLiving();
            if (event.getItem().getItem() != Items.NAME_TAG && event.getItem().getItem() != Items.LEAD && getPointedEntity(player.world, player, 5.0D) instanceof BasicEntityShip) {
                event.setDuration(0);
                event.setCanceled(true);
                syncPlayerHand(player, player.getActiveHand());
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onFillBucket(FillBucketEvent event) {
        if (getPointedEntity(event.getEntityPlayer().world, event.getEntityPlayer(), 5.0D) instanceof BasicEntityShip) {
            event.setCanceled(true);
            syncPlayerHand(event.getEntityPlayer(), EnumHand.MAIN_HAND);
            syncPlayerHand(event.getEntityPlayer(), EnumHand.OFF_HAND);
        }
    }

    private static void syncPlayerHand(EntityPlayer player, EnumHand hand) {
        if (player instanceof EntityPlayerMP) {
            EntityPlayerMP playerMP = (EntityPlayerMP) player;
            EntityEquipmentSlot slot = (hand == EnumHand.MAIN_HAND)? EntityEquipmentSlot.MAINHAND : EntityEquipmentSlot.OFFHAND;
            playerMP.connection.sendPacket(new SPacketEntityEquipment(player.getEntityId(), slot, player.getHeldItem(hand)));
        }
    }

    @Nullable
    private static Entity getPointedEntity(World world, EntityPlayer player, double range) {
        Entity pointedEntity = null;
        Vec3d eyePosition = player.getPositionEyes(1.0F);
        Vec3d lookVector = player.getLook(1.0F);
        Vec3d reachVector = eyePosition.add(lookVector.scale(range));
        List<Entity> candidates = world.getEntitiesInAABBexcluding(
                player,
                player.getEntityBoundingBox().expand(lookVector.x * range, lookVector.y * range, lookVector.z * range).grow(1.0D),
                entity -> entity!= null && entity.canBeCollidedWith()
        );
        double closestDistanceSq = -1.0D;
        for (Entity entity : candidates) {
            AxisAlignedBB entityBB = entity.getEntityBoundingBox().grow(entity.getCollisionBorderSize());
            RayTraceResult intercept = entityBB.calculateIntercept(eyePosition, reachVector);
            if (intercept!= null) {
                double distSq = eyePosition.squareDistanceTo(intercept.hitVec);
                if (closestDistanceSq == -1.0D || distSq < closestDistanceSq) {
                    closestDistanceSq = distSq;
                    pointedEntity = entity;
                }
            }
        }
        return pointedEntity;
    }
```

##### Note : 由于该程序会在深海栖舰面前禁止所有右键交互，根据其他MOD对右键事件的实现情况，可能会引发不兼容的问题。


## 优先权 : ■■□□□

#### Issue : 请求为道具“深海日志”添加日文翻译
#### Effect : 我尝试为书中缺失的日文翻译进行了制作，总觉得比完全留白要好一些，希望能够派上用场

#### How to fix : 从 Github 下载并替换 ja_jp.lang。


## 优先权 : ■■□□□

#### Issue : 深海船只不能使用梯子上下。
#### Effect : 玩家等待深海船传送的时间
#### Cause : 机载 A* 搜索算法不具备识别梯子的能力。

#### How to fix : 使用补丁加强深海船只的腿脚。

shincolle/reference/Enums.java

```
public enum EnumPathType {
...
	FENCE,
+++	LADDER
        ...
```

shincolle/ai/path/ShipPath.java

```
+++ public ShipPathPoint[] getPathPoints(){return this.points;}
```

shincolle/ai/path/ShipPathPoint.java

```
+++ public void initForSearch(ShipPathPoint target) {
        setTotalPathDistance(0f);
        float d = distanceManhattan(target);
        setDistanceToNext(d);
        setDistanceToTarget(d);
        setDistanceFromOrigin(0f);
        setPrevious(null);
        setVisited(false);
        setIndex(-1);
    }

        public boolean initPathParameters(ShipPathPoint parent, ShipPathPoint target, float range) {
        float dist = parent.distanceManhattan(this);
        float newFrom = parent.getDistanceFromOrigin() + dist;
        float dist2 = parent.getTotalPathDistance() + dist;
        if (newFrom >= range || (isAssigned() && dist2 >= getTotalPathDistance())) {
            return false;
        }
        setDistanceFromOrigin(newFrom);
        setPrevious(parent);
        setTotalPathDistance(dist2);
        setDistanceToNext(distanceManhattan(target));
        return true;
    }

    public void setIndex(int index)     { this.index = index; }
    public float getTotalPathDistance() { return totalPathDistance; }
    public void setTotalPathDistance(float d) { this.totalPathDistance = d; }
    public float getDistanceToNext()    { return distanceToNext; }
    public void setDistanceToNext(float d) { this.distanceToNext = d; }
    public float getDistanceToTarget()  { return distanceToTarget; }
    public void setDistanceToTarget(float d) { this.distanceToTarget = d; }
    public ShipPathPoint getPrevious()  { return previous; }
    public void setPrevious(ShipPathPoint p) { this.previous = p; }
    public boolean isVisited()          { return visited; }
    public void setVisited(boolean v)   { this.visited = v; }
    public float getDistanceFromOrigin() { return distanceFromOrigin; }
    public void setDistanceFromOrigin(float d) { this.distanceFromOrigin = d; }
```

shincolle/ai/path/ShipPathFinder.java
从 Github 下载并替换 ShipPathFinder.java。

##### Note : 由于采用了蛮力式的实现，实际爬上梯子的成功率可能连20%都不到，就当作给自己一点心理安慰吧。


-----------------------------------------------------------------------------------------------


ShinColle MOD 1.12.2移植の際に見つけた不具合・改善案を以下に記しておきます。


## 重要度 : ■■■■■

#### Issue : ブロック"深海提督ノ机"を設置して破壊した後、同じ位置に異なるタイルエンティティ（例:"深海ノ熱水噴出孔"）を設置すると、"深海提督ノ机"のテクスチャが重なって表示される。
#### Effect : テクスチャの表示バグ、タイルを右クリック時にインベントリアイテムのスワップや消失、予期せぬエラー発生の可能性
#### Cause : タイルエンティティの削除処理の実装漏れ

#### How to fix : タイルエンティティ削除処理の追加

shincolle/block/BlockDesk.java

```
+++ @Override
    public void onBlockHarvested(World worldIn, BlockPos pos, IBlockState state, EntityPlayer player) {
        super.onBlockHarvested(worldIn, pos, state, player);
        if (worldIn.isRemote) {
            worldIn.removeTileEntity(pos);
            worldIn.notifyBlockUpdate(pos, state, Blocks.AIR.getDefaultState(), 3);
            worldIn.markBlockRangeForRenderUpdate(pos, pos);
        }
    }
```

## 重要度 : ■■■■□

#### Issue : Metamorph MODで深海棲艦に変身すると体力が4に固定される、もしくは体力が4と25~35あたりで高速に上書きされる。
#### Effect : 体力表示に不具合、実際の最大体力の減少？
#### Cause : Metamorph MODの仕様を逸脱した.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue()による最大体力の上書き合戦

#### How to fix : morph.settingsを使った最大体力の指定

shincolle/intermod/MetamorphHelper.java

```
public static void onPlayerTickHelper(EntityPlayer player) {
...
	if (im != null && im.getCurrentMorph() instanceof EntityMorph) {
	...
		if (em != null && em.getEntity() instanceof IShipMorph) {

+++			AbstractMorph morph = Morphing.get(player).getCurrentMorph();
                EntityLivingBase target = ((EntityMorph) morph).getEntity(player.world);
                if(target instanceof BasicEntityShip){
                    morph.settings.health = (int)(20.0 + ((BasicEntityShip) target).getAttrs().getAttrsBuffed(0) * ConfigHandler.morphHPRatio);
                } else if(target instanceof BasicEntityShipHostile) {
                    morph.settings.health = 20;
                }

		...
```

Shincolle/entity/BasicEntityShip.java

```
public void calcShipAttributes(int flag, boolean sync) {
...
--- if (this.morphHost != null) {		this.morphHost.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(20D + this.shipAttrs.getAttrsBuffed(ID.Attrs.HP) * ConfigHandler.morphHPRatio);
    }
```

##### Note : この修正はMorphの体力表示問題のみ解決するものであり、潜在的な不具合発生のリスクがあります。

## 重要度 : ■■■□□

#### Issue : アイテム"ヲ級ノ指揮ノ杖"で深海棲艦をクリックしたときに、チームパーティクルの更新が最大32tickほど遅延する
#### Effect : ユーザーエクスペリエンスの低下
#### Cause : チームパーティクルの更新が32tickおきに固定されているため

#### How to fix : 深海棲艦クリック時にパーティクルの更新処理を追加

shincolle/item/PointerItem.java

```
+++ private int coolDown = -1;

...

    @Override
    public boolean onEntitySwing(EntityLivingBase entity, ItemStack item) {
    ...
        RayTraceResult hitObj = EntityHelper.getPlayerMouseOverEntity(64D, 1F, exlist, true, false);
        if (hitObj != null) {
        ...
            if (hitObj.entityHit instanceof BasicEntityShip || hitObj.entityHit instanceof BasicEntityMount) {
            ...
                if (TeamHelper.checkSameOwner(player, ship) && capa != null) {
                ...
                    if (keySet.keyBindSneak.isKeyDown()) {
                    ...
                        if (i >= 0) {
                        ...

+++                            if((EntityHelper.getPointerInUse(player) != null && stack.getMetadata() < 3) || ConfigHandler.alwaysShowTeamParticle) {
                                coolDown = 2;
                            }

                            return true;
                        }
                    } else {
                    ...

+++                       if((EntityHelper.getPointerInUse(player) != null && item.getMetadata() < 3) || ConfigHandler.alwaysShowTeamParticle) {
                           coolDown = 2;
                       }

                       return true;
                   }

...

    @Override
    public void onUpdate(ItemStack stack, World world, Entity entity, int slot, boolean inUse) {
    ...
    if (player instanceof EntityPlayer && EntityHelper.getPointerInUse((EntityPlayer) player) != null && item.getMetadata() < 3 || ConfigHandler.alwaysShowTeamParticle) {
        if (world.isRemote) {

+++            if(coolDown > 0){
                coolDown --;
            }

⇄⇄⇄          if (entity.ticksExisted % 32 == 0 || coolDown == 0) {
            ...

```

## 重要度 : ■■■□□

#### Issue : 右クリックイベントが存在するアイテム（溶岩バケツ、エンダーパールなど）を所持しながら深海棲艦のGUIを表示しようとしたときに、右クリックイベントが発動してしまう。（溶岩をたらしたり、深海棲艦にぶつかりに行ったりするなど）
#### Effect : プレイヤーの意図せぬ操作の実行や、予期せぬ不具合発生の可能性
#### Cause : 右クリックイベントの回避処理の未実装

#### How to fix : 右クリックイベントの回避処理実装

shincolle/handler/EventHandler.java

```
+++@SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getItemStack().getItem() != Items.NAME_TAG && event.getItemStack().getItem() != Items.LEAD && getPointedEntity(event.getEntityPlayer().world, event.getEntityPlayer(), 5.0D) instanceof BasicEntityShip) {
            event.setResult(Event.Result.DENY);
            event.setCanceled(true);
            syncPlayerHand(event.getEntityPlayer(), event.getHand());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getItemStack().getItem() != Items.NAME_TAG && event.getItemStack().getItem() != Items.LEAD && getPointedEntity(event.getEntityPlayer().world, event.getEntityPlayer(), 5.0D) instanceof BasicEntityShip) {
            event.setUseBlock(Event.Result.DENY);
            event.setUseItem(Event.Result.DENY);
            event.setCanceled(true);
            syncPlayerHand(event.getEntityPlayer(), event.getHand());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onUseItemStart(LivingEntityUseItemEvent.Start event) {
        if (event.getEntityLiving() instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) event.getEntityLiving();
            if (event.getItem().getItem() != Items.NAME_TAG && event.getItem().getItem() != Items.LEAD && getPointedEntity(player.world, player, 5.0D) instanceof BasicEntityShip) {
                event.setDuration(0);
                event.setCanceled(true);
                syncPlayerHand(player, player.getActiveHand());
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onFillBucket(FillBucketEvent event) {
        if (getPointedEntity(event.getEntityPlayer().world, event.getEntityPlayer(), 5.0D) instanceof BasicEntityShip) {
            event.setCanceled(true);
            syncPlayerHand(event.getEntityPlayer(), EnumHand.MAIN_HAND);
            syncPlayerHand(event.getEntityPlayer(), EnumHand.OFF_HAND);
        }
    }

    private static void syncPlayerHand(EntityPlayer player, EnumHand hand) {
        if (player instanceof EntityPlayerMP) {
            EntityPlayerMP playerMP = (EntityPlayerMP) player;
            EntityEquipmentSlot slot = (hand == EnumHand.MAIN_HAND)? EntityEquipmentSlot.MAINHAND : EntityEquipmentSlot.OFFHAND;
            playerMP.connection.sendPacket(new SPacketEntityEquipment(player.getEntityId(), slot, player.getHeldItem(hand)));
        }
    }

    @Nullable
    private static Entity getPointedEntity(World world, EntityPlayer player, double range) {
        Entity pointedEntity = null;
        Vec3d eyePosition = player.getPositionEyes(1.0F);
        Vec3d lookVector = player.getLook(1.0F);
        Vec3d reachVector = eyePosition.add(lookVector.scale(range));
        List<Entity> candidates = world.getEntitiesInAABBexcluding(
                player,
                player.getEntityBoundingBox().expand(lookVector.x * range, lookVector.y * range, lookVector.z * range).grow(1.0D),
                entity -> entity!= null && entity.canBeCollidedWith()
        );
        double closestDistanceSq = -1.0D;
        for (Entity entity : candidates) {
            AxisAlignedBB entityBB = entity.getEntityBoundingBox().grow(entity.getCollisionBorderSize());
            RayTraceResult intercept = entityBB.calculateIntercept(eyePosition, reachVector);
            if (intercept!= null) {
                double distSq = eyePosition.squareDistanceTo(intercept.hitVec);
                if (closestDistanceSq == -1.0D || distSq < closestDistanceSq) {
                    closestDistanceSq = distSq;
                    pointedEntity = entity;
                }
            }
        }
        return pointedEntity;
    }
```

##### Note : このプログラムは、深海棲艦の前ではすべての右クリックイベントを禁止するため、他MODの右クリックイベント実装状況によっては不具合が発生する可能性があります。

## 重要度 : ■■□□□

#### Issue : アイテム"深海提督ノ航海日誌(説明書)"の日本語訳追加のお願い
#### Effect : 本の欠けている日本語訳を作成してみました。全くの空白よりかはマシだと思いますので、お使いいただければ幸いです。

#### How to fix : Githubよりja_jp.langのダウンロード、置き換え

## 重要度 : ■■□□□

#### Issue : 深海棲艦がはしごを使って昇降することができない
#### Effect : 深海棲艦がテレポートするまでプレイヤーに待ちの時間が発生する
#### Cause : 搭載されているA*探索アルゴリズムでは、はしごを認識する機能が存在しない

#### How to fix : 深海棲艦の足腰強化パッチの適用

shincolle/reference/Enums.java

```
public enum EnumPathType {
...
	FENCE,
+++	LADDER
        ...
```

shincolle/ai/path/ShipPath.java

```
+++ public ShipPathPoint[] getPathPoints(){return this.points;}
```

shincolle/ai/path/ShipPathPoint.java

```
+++ public void initForSearch(ShipPathPoint target) {
        setTotalPathDistance(0f);
        float d = distanceManhattan(target);
        setDistanceToNext(d);
        setDistanceToTarget(d);
        setDistanceFromOrigin(0f);
        setPrevious(null);
        setVisited(false);
        setIndex(-1);
    }

        public boolean initPathParameters(ShipPathPoint parent, ShipPathPoint target, float range) {
        float dist = parent.distanceManhattan(this);
        float newFrom = parent.getDistanceFromOrigin() + dist;
        float dist2 = parent.getTotalPathDistance() + dist;
        if (newFrom >= range || (isAssigned() && dist2 >= getTotalPathDistance())) {
            return false;
        }
        setDistanceFromOrigin(newFrom);
        setPrevious(parent);
        setTotalPathDistance(dist2);
        setDistanceToNext(distanceManhattan(target));
        return true;
    }

    public void setIndex(int index)     { this.index = index; }
    public float getTotalPathDistance() { return totalPathDistance; }
    public void setTotalPathDistance(float d) { this.totalPathDistance = d; }
    public float getDistanceToNext()    { return distanceToNext; }
    public void setDistanceToNext(float d) { this.distanceToNext = d; }
    public float getDistanceToTarget()  { return distanceToTarget; }
    public void setDistanceToTarget(float d) { this.distanceToTarget = d; }
    public ShipPathPoint getPrevious()  { return previous; }
    public void setPrevious(ShipPathPoint p) { this.previous = p; }
    public boolean isVisited()          { return visited; }
    public void setVisited(boolean v)   { this.visited = v; }
    public float getDistanceFromOrigin() { return distanceFromOrigin; }
    public void setDistanceFromOrigin(float d) { this.distanceFromOrigin = d; }
```

shincolle/ai/path/ShipPathFinder.java
GithubよりShipPathFinder.javaのダウンロード、置き換え

##### Note : 荒業による実装のため、実際にはしごを登れる確率は20%もないかもしれません。気休め程度に思っておいてください。
