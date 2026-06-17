package com.example.dianping.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.dianping.dto.Result;
import com.example.dianping.dto.ScrollResult;
import com.example.dianping.dto.UserDTO;
import com.example.dianping.entity.Blog;
import com.example.dianping.entity.Follow;
import com.example.dianping.entity.User;
import com.example.dianping.mapper.BlogMapper;
import com.example.dianping.service.IBlogService;
import com.example.dianping.service.IFollowService;
import com.example.dianping.service.IUserService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.dianping.utils.SystemConstants;
import com.example.dianping.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestParam;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IUserService userService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IFollowService followService;

    @Override
    public Result queryBlogById(Long id) {
        Blog blog = getById( id);
        if(blog == null){
            return Result.fail("数据不存在");
        }
        queryBlogUser(blog);
        isBlogLiked(blog);
        return Result.ok(blog);
    }

    private void isBlogLiked(Blog blog) {
        UserDTO user = UserHolder.getUser();
        if(user == null){
            return;
        }
        Long userId = UserHolder.getUser().getId();
        String key = "blog:liked:" + blog.getId();
        Double score= stringRedisTemplate.opsForZSet().score(key, userId.toString());
        blog.setIsLike(score!= null);
    }

    @Override
    public Result likeBlog(Long id) {
        Long userId = UserHolder.getUser().getId();
        String key = "blog:liked:" + id;
       Double  score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
       if(score!= null){
           //如果已点赞，取消点赞
           boolean isSuccess = update().setSql("liked = liked - 1").eq("id",id).update();
           stringRedisTemplate.opsForZSet().remove(key,userId.toString());
       }else{
          boolean isSuccess = update().setSql("liked = liked + 1").eq("id",id).update();
          if(!isSuccess){
              return Result.fail("点赞失败");
          }
           //否则，点赞
           stringRedisTemplate.opsForZSet().add(key,userId.toString(),System.currentTimeMillis());
       }
        return Result.ok();
    }

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        if (user != null) {
            blog.setName(user.getNickName());
            blog.setIcon(user.getIcon());
        }
    }
    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page =query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog ->{
           this.queryBlogUser(blog);
           this.isBlogLiked(blog);
        });
        return Result.ok(records);
    }

    @Override
    public Result queryBlogLikes(Long id) {
        String key = "blog:liked:" + id;
       Set< String> top5= stringRedisTemplate.opsForZSet().range(key,0,4);
       if(top5 == null || top5.isEmpty()){
           return Result.ok();
       }
       List< Long> ids=top5.stream().map(Long::valueOf).collect(Collectors.toList());
       String idStr= StrUtil.join(",",ids);
      List<UserDTO> users = userService.query().in("id",ids).last("ORDER BY FIELD(id,"+idStr+")").list().stream().
              map(user -> BeanUtil.copyProperties(user, UserDTO.class)).
              collect(Collectors.toList());
        return Result.ok(users);
    }

    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
      boolean isSuccess = save( blog);
      if(!isSuccess){
          return Result.fail("新增博文失败");
      }
        List<Follow> follows = followService.query()
                .eq("follow_user_id", user.getId())
                .list();   for(Follow follow:follows)
       {
           Long userId = follow.getUserId();
           String key = "feed:" + userId;
           stringRedisTemplate.opsForZSet().
                   add(key,blog.getId().toString(),System.currentTimeMillis());
       }
        // 返回id
        return Result.ok(blog.getId());
    }

    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        Long userId = UserHolder.getUser().getId();
        String key = "feed:" + userId;
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet().
                reverseRangeByScoreWithScores(key, 0, max, offset, 2);
        if(typedTuples == null || typedTuples.isEmpty()){
            return Result.ok();
        }
        List<Long> ids = new ArrayList<>(typedTuples.size());
        long minTime=0;
        int os=1;
        for(ZSetOperations.TypedTuple<String> tuple:typedTuples)
        {
            String blogId = tuple.getValue();
            ids.add(Long.valueOf(blogId));
            long time = tuple.getScore().longValue();
            if(time==minTime)
            {
                os++;
            }
            else
            {
                os=1;
            }
            minTime = time;
        }
        List<Blog> blogs = query().in("id", ids).last("ORDER BY FIELD(id,"+ StrUtil.join(",",ids)+")").list();
        for(Blog blog:blogs)
        {
            queryBlogUser(blog);
            isBlogLiked(blog);
        }
        ScrollResult r=new ScrollResult();
        r.setList(blogs);
        r.setOffset(os);
        r.setMinTime(minTime);
        return Result.ok(r);
    }

}
