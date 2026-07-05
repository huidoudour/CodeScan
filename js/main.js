// CodeScan 项目页面 JavaScript - 基于 huidoudour.github.io 交互风格

// 页面加载完成后初始化
document.addEventListener('DOMContentLoaded', function() {
    // 设置当前日期
    const now = new Date();
    const options = { year: 'numeric', month: 'long', day: 'numeric' };
    const currentDateElement = document.getElementById('currentDate');
    if (currentDateElement) {
        currentDateElement.textContent = now.toLocaleDateString('zh-CN', options);
    }
    
    // 初始化返回顶部按钮
    initBackToTopButton();
    
    // 添加卡片动画延迟
    initCardAnimations();
});

// 初始化返回顶部按钮
function initBackToTopButton() {
    // 创建返回顶部按钮
    const backToTopBtn = document.createElement('div');
    backToTopBtn.className = 'back-to-top';
    backToTopBtn.innerHTML = '<i class="fas fa-arrow-up"></i>';
    backToTopBtn.title = '返回顶部';
    document.body.appendChild(backToTopBtn);
    
    // 监听滚动事件
    window.addEventListener('scroll', function() {
        if (window.pageYOffset > 300) {
            backToTopBtn.classList.add('show');
        } else {
            backToTopBtn.classList.remove('show');
        }
    });
    
    // 点击返回顶部
    backToTopBtn.addEventListener('click', function() {
        window.scrollTo({
            top: 0,
            behavior: 'smooth'
        });
    });
}

// 初始化卡片动画
function initCardAnimations() {
    const cards = document.querySelectorAll('.feature-card');
    cards.forEach((card, index) => {
        setTimeout(() => {
            card.style.opacity = '1';
        }, 100 + index * 100);
    });
}

// 页面可见性变化时的标题变化效果
document.addEventListener('visibilitychange', function () {
    var isHidden = document.hidden;
    if (isHidden) {
        document.title = "CodeScan|不要走嘛(*´д`*)";
    } else {
        setTimeout(() => {
            document.title = "CodeScan|好耶,回来了(づ￣3￣)づ╭❤～";
        }, 1000);
        setTimeout(() => {
            document.title = "CodeScan - 二维码扫描应用";
        }, 2200);
    }
});

// 添加控制台欢迎信息
console.log("🌟 欢迎来到 CodeScan 项目页面！\n📱 快速、准确、易用的二维码扫描解决方案\n💡 希望这个应用能对你有所帮助！\n☕ 感谢访问，记得常来哦~");

// 添加鼠标点击特效
document.addEventListener('click', function(e) {
    if (e.target.tagName !== 'A' && e.target.tagName !== 'BUTTON') {
        const heart = document.createElement('div');
        heart.innerHTML = '❤';
        heart.style.position = 'fixed';
        heart.style.left = (e.clientX - 10) + 'px';
        heart.style.top = (e.clientY - 20) + 'px';
        heart.style.fontSize = '20px';
        heart.style.color = '#ff6b6b';
        heart.style.pointerEvents = 'none';
        heart.style.zIndex = '9999';
        heart.style.userSelect = 'none';
        document.body.appendChild(heart);
        
        setTimeout(() => {
            heart.style.transition = 'all 1s ease-out';
            heart.style.opacity = '0';
            heart.style.transform = 'translateY(-30px)';
            setTimeout(() => {
                if (document.body.contains(heart)) {
                    document.body.removeChild(heart);
                }
            }, 1000);
        }, 10);
    }
});

// 添加页面加载完成后的动画效果
window.addEventListener('load', function() {
    // 触发所有卡片的动画
    const cards = document.querySelectorAll('.feature-card');
    cards.forEach((card, index) => {
        setTimeout(() => {
            card.style.animationDelay = `${index * 0.1}s`;
            card.style.opacity = '1';
        }, 100);
    });
});
